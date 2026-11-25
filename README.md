# Coupon Service

REST service for creating discount coupons and redeeming them. A coupon has a case-insensitive
unique code, a usage limit and a country; it can be redeemed only from that country (by client IP),
first come first served, and at most once per user.

**Stack:** Java 25 (LTS), Spring Boot 4.2, PostgreSQL 19, Hibernate 7, Flyway, Gradle 9,
JUnit 6, Testcontainers 2, WireMock, ArchUnit.

## Quick start

```bash
# Everything in containers (PostgreSQL 19 + the service on :8080)
docker compose --profile app up --build

# Or run the service locally; spring-boot-docker-compose starts PostgreSQL from compose.yaml
./gradlew bootRun
```

```bash
# Create a coupon (codes are case-insensitive and stored upper case)
curl -i -X POST localhost:8080/api/v1/coupons -H 'Content-Type: application/json' \
     -d '{"code": "wiosna", "maxUses": 2, "country": "PL"}'

# Redeem it as a client from Poland. The service trusts X-Forwarded-For only from trusted
# proxies; localhost and private networks are trusted by default, so locally it stands in
# for the client IP. 212.77.98.9 is in Poland, 46.4.0.1 in Germany.
curl -i -X POST localhost:8080/api/v1/coupons/WIOSNA/redemptions -H 'Content-Type: application/json' \
     -H 'X-Forwarded-For: 212.77.98.9' -d '{"userId": "user-1"}'
```

More ready-made calls are in [`requests.http`](requests.http). Swagger UI: <http://localhost:8080/swagger-ui.html>.
Metrics: `/actuator/prometheus`, health probes: `/actuator/health/{liveness,readiness}`.

Tests need Docker (Testcontainers): `./gradlew build` runs unit, web, integration, end-to-end and
architecture tests plus the coverage gate.

## API

| Method and path | Body | Success |
| --- | --- | --- |
| `POST /api/v1/coupons` | `{"code", "maxUses", "country", "validFrom"?, "validUntil"?}` | `201` with `Location` and the coupon |
| `GET /api/v1/coupons/{code}` | | `200` with the coupon |
| `POST /api/v1/coupons/{code}/redemptions` | `{"userId"}` | `201` with `Location`, `couponCode`, `userId`, `redeemedAt` |
| `GET /api/v1/coupons/{code}/redemptions/{userId}` | | `200` with the redemption |

`validFrom` and `validUntil` are optional ISO 8601 instants. The period is half-open: a coupon
can be redeemed from `validFrom` (inclusive) until `validUntil` (exclusive), so
`"validUntil": "2026-12-01T00:00:00Z"` means "through the whole of November". A missing side is open.

The `GET` on a redemption is there for a client that lost the answer to its `POST` (timeout,
dropped connection): it checks whether the redemption went through instead of retrying blindly.

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with an `errorCode`
member. Clients should branch on `errorCode`, since two problems can share a status.

| Situation | Status | `errorCode` |
| --- | --- | --- |
| Missing field, bad code or country format, `maxUses` < 1, `validUntil` not after `validFrom` or not in the future | 400 | `VALIDATION_FAILED` |
| Body is not valid JSON | 400 | `MALFORMED_REQUEST` |
| Code already exists, in any letter case | 409 | `COUPON_CODE_ALREADY_EXISTS` |
| Coupon does not exist | 404 | `COUPON_NOT_FOUND` |
| Coupon exists, but this user has not redeemed it (`GET` on a redemption) | 404 | `REDEMPTION_NOT_FOUND` |
| Client country differs from the coupon's, or cannot be determined | 403 | `COUPON_COUNTRY_NOT_ALLOWED` |
| Before `validFrom` | 409 | `COUPON_NOT_YET_VALID` |
| At or after `validUntil` | 409 | `COUPON_EXPIRED` |
| Usage limit reached | 409 | `COUPON_USAGE_LIMIT_REACHED` |
| This user already redeemed this coupon | 409 | `COUPON_ALREADY_REDEEMED` |
| Geolocation provider unavailable | 503 + `Retry-After` | `GEOLOCATION_UNAVAILABLE` |

## Architecture

Hexagonal, in one module. The domain and the use cases have no framework dependencies;
Spring, Hibernate and HTTP live in adapters behind ports. ArchUnit enforces it.

```mermaid
flowchart TB
    client([Client / load balancer]) -->|X-Forwarded-For| tomcat[Tomcat RemoteIpValve]
    tomcat --> web[Web adapter<br/>controllers, DTOs, problem details]
    subgraph core [Core: no framework dependencies]
        app[Use cases<br/>CreateCouponHandler, RedeemCouponHandler, CouponQueries]
        domain[Domain<br/>Coupon, CouponCode, CountryCode, UserId, IpAddress, RedemptionResult]
        ports[Ports<br/>CouponRepository, CountryResolver]
        app --> domain
        app --> ports
    end
    web --> app
    ports --> persistence[Persistence adapter<br/>Hibernate + explicit SQL, Flyway]
    ports --> geo[Geolocation adapter<br/>HTTP service client, retry, limit, cache]
    persistence --> db[(PostgreSQL 19)]
    geo --> countryis([api.country.is])
```

```
pl.hadacz.coupons
├── coupon
│   ├── domain           value objects, Coupon, RedemptionResult (sealed)
│   ├── application      use cases; port/ holds CouponRepository and CountryResolver
│   └── adapter
│       ├── web          controllers, request/response records, problem details, metrics
│       ├── persistence  JPA entity, Spring Data repository, port implementation
│       └── geolocation  country.is client, gateway (retry, limit), resolver (cache)
└── config               wiring of the framework-free use cases, OpenAPI
```

Commands (create, redeem) go through the domain and a transaction; the query side
(`CouponQueries`) returns a view and could read from a replica. That is all the CQRS this
service needs: with two tables a separate read model would be overhead.

## The core: concurrent redemption

The usage limit and "one use per user" are enforced by PostgreSQL, not by the JVM. Two
statements run in one short `READ COMMITTED` transaction:

```sql
-- 1. Record the redemption; 0 rows means this user already redeemed the coupon.
INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at)
SELECT c.id, :userId, :redeemedAt FROM coupons c WHERE c.code = :code
ON CONFLICT (coupon_id, user_id) DO NOTHING;

-- 2. Consume one use only if one is left; 0 rows means the limit is reached -> ROLLBACK.
UPDATE coupons SET current_uses = current_uses + 1
WHERE code = :code AND current_uses < max_uses;
```

```mermaid
flowchart TB
    s1[Read coupon by code, no lock] -->|missing| e404[404 COUPON_NOT_FOUND]
    s1 --> sv{Within the validity period now?}
    sv -->|before / after| e409v[409 COUPON_NOT_YET_VALID / COUPON_EXPIRED]
    sv --> s2{Exhausted in the snapshot?}
    s2 -->|yes| e409a[409 COUPON_USAGE_LIMIT_REACHED]
    s2 --> s3[Resolve country: cache, then api.country.is]
    s3 -->|provider down| e503[503 GEOLOCATION_UNAVAILABLE]
    s3 --> s4{Client in the coupon's country?}
    s4 -->|no or unknown| e403[403 COUPON_COUNTRY_NOT_ALLOWED]
    subgraph tx [Transaction, READ COMMITTED]
        s5[INSERT ... ON CONFLICT DO NOTHING]
        s6[UPDATE ... WHERE current_uses #lt; max_uses]
    end
    s4 -->|yes| s5
    s5 -->|0 rows| e409b[409 COUPON_ALREADY_REDEEMED]
    s5 --> s6
    s6 -->|0 rows, ROLLBACK| e409c[409 COUPON_USAGE_LIMIT_REACHED]
    s6 -->|COMMIT| ok[201 Created]
```

Every refusal before the transaction (dates, exhausted, country, provider down) first checks
whether this user already redeemed the coupon, and if so answers `COUPON_ALREADY_REDEEMED`
instead: a user who took the last use and retries is told that, not that the coupon ran out.
The lookup is one indexed query on the refusal path only; a successful redemption does not pay for it.

Why this is correct:

- **No over-redemption.** A second `UPDATE` of the same row waits for the row lock; after the
  first transaction commits, PostgreSQL re-evaluates `current_uses < max_uses` against the newest
  row version. The counter cannot pass the limit and no increment is lost.
- **One use per user.** A concurrent `INSERT` of the same (coupon, user) pair waits for the other
  transaction: after its commit it conflicts (0 rows), after its rollback it proceeds.
- **All or nothing.** If the `UPDATE` finds no use left, the transaction rolls back and the
  redemption row disappears with it.
- **Short locks, no deadlocks.** The `INSERT` goes first, so the hot coupon row is locked only from
  the `UPDATE` to `COMMIT`. The foreign key check takes `FOR KEY SHARE` on the coupon row and the
  counter update takes `FOR NO KEY UPDATE`; those modes do not conflict, and every transaction
  locks in the same order.
- **No I/O inside the transaction.** Geolocation happens before it, so a slow provider cannot hold
  connections or locks.
- **Any number of instances.** The database is the only shared state.
- **Safe fast path.** The exhausted check reads without locks. The counter only grows, so a snapshot that says
  "exhausted" is never wrong, and exhausted coupons do not cost a geolocation call.
- **One clock reading.** The instant checked against the validity period is the one stored as
  `redeemed_at`, so a recorded redemption never falls outside the coupon's period, even when the
  request straddles `validUntil`.

`CouponRedemptionConcurrencyTest` races 500 virtual threads for 50 uses (exactly 50 win) and one
user against itself (exactly one wins) on a real PostgreSQL. Tests raise confidence; the guarantee
comes from the SQL above.

## Geolocation and the client IP

- **Client IP.** `server.forward-headers-strategy=native` enables Tomcat's `RemoteIpValve`:
  `X-Forwarded-For` is used only when the request comes from a trusted proxy
  (`server.tomcat.remoteip.internal-proxies`). In production that must be the load balancer's
  addresses. Reading the header directly would let any client claim a Polish IP.
- **Provider.** [country.is](https://country.is): HTTPS, no API key, free also for commercial use,
  data from MaxMind GeoLite2 and Cloudflare refreshed daily. Private, loopback and similar
  addresses are never sent to it.
- **Resilience.** Declarative HTTP service client with 500 ms connect and 1 s read timeouts;
  `@Retryable` for 5xx and I/O errors only; `@ConcurrencyLimit(10)` as a bulkhead, because with
  virtual threads no thread pool bounds outgoing calls; answers cached per IP in Caffeine for 24 h,
  failures never cached.
- **Fail closed.** If the country cannot be verified, the coupon is not redeemed (503 with
  `Retry-After`). A country-restricted coupon is a business rule; whether to fail open instead is a
  business decision, not a technical default.
- **GDPR.** An IP address is personal data. The service does not store it or log it. Sending it to
  a third party is still a transfer; see the production notes below.

## Testing

| Level | Tools | What it checks |
| --- | --- | --- |
| Domain | JUnit 6, AssertJ | Code normalization (also under a Turkish default locale), ISO countries, IP classification, coupon invariants, validity period bounds |
| Use cases | In-memory fakes, fixed `Clock` | Every `RedemptionResult`, order of checks, no geolocation call for missing, expired or exhausted coupons |
| Web | `@WebMvcTest`, `MockMvcTester` | Status codes, `Location`, problem details, validation |
| Persistence | Testcontainers PostgreSQL 19 | Schema constraints, duplicate codes, the atomic redemption |
| Concurrency | Virtual threads, start gate | Limit never exceeded, one use per user, racing creations of `WIOSNA`/`wiosna` |
| Geolocation | WireMock | Mapping, cache, retries on 5xx, no retry on 429, timeouts |
| End to end | `RestTestClient` on a random port | Full flow through Tomcat, including `X-Forwarded-For` |
| Architecture | ArchUnit | Core free of frameworks, adapters independent |

The build fails below 90% line coverage of the domain and application packages.
`scripts/RedeemStorm.java` (run with `java scripts/RedeemStorm.java WIOSNA 1000`) checks the same
guarantee against a running service over HTTP.

## Decisions

**ADR 1: Enforce the limit atomically in PostgreSQL.** A conditional `UPDATE` plus
`INSERT ... ON CONFLICT` in one transaction. Rejected: JVM locks (work within one instance only);
load-modify-save with JPA (classic lost update); optimistic locking with `@Version` (a popular coupon
turns into a storm of conflicts and retries); `SELECT ... FOR UPDATE` (holds the lock through the
read, the Java logic and the write); `SERIALIZABLE` (needs retry handling for no benefit, since one
statement is already atomic); a Redis counter (a second source of truth and more infrastructure).

**ADR 2: Normalize codes in the domain, enforce uniqueness in the database.** `CouponCode` upper-cases
with `Locale.ROOT` and the column has a plain `UNIQUE` constraint, so a race between `WIOSNA` and
`wiosna` has exactly one winner. Rejected: `citext` or a unique index on `lower(code)`; they work
but hide the business rule in the database.

**ADR 3: Hibernate for CRUD, explicit SQL for the redemption.** The team's stack is Hibernate, and it
keeps the create/read code short. The redemption needs exact SQL semantics, so it is a JPQL bulk
update and a native insert, both `Propagation.MANDATORY` so they cannot run outside the redemption
transaction. The bulk update bypasses the persistence context, which is safe because that transaction
never loads the entity. Also deliberate: `open-in-view=false`, `ddl-auto=validate` against Flyway,
UUIDv7 ids generated by Hibernate (so `save` is a persist, not a merge), and `saveAndFlush` on create
so a duplicate code surfaces inside the adapter.

**ADR 4: Business outcomes as values.** `RedemptionResult` is a sealed interface of records and the
controller maps it with an exhaustive `switch`: a new outcome does not compile until it has an HTTP
mapping. Exceptions are reserved for invalid input and technical failures.

**ADR 5: country.is behind a port, cached, failing closed.** Chosen because reviewers can run the
project without an API key. Rejected for now: IPinfo Lite (needs a token), ip-api.com (free tier is
HTTP-only and non-commercial).

**ADR 6: Client IP only through `RemoteIpValve` with trusted proxies.** Rejected: Spring's
`ForwardedHeaderFilter` (trusts every header) and reading `X-Forwarded-For` by hand.

**ADR 7: Virtual threads with a deliberately sized connection pool.** Blocking code stays simple;
the pool (20, 2 s timeout) is the real concurrency limit for the database and fails fast under
overload. Rejected: WebFlux with R2DBC, more complex for no gain here.

## Scaling and production notes

- **Hot row.** All redemptions of one coupon serialize on one row; different coupons do not block
  each other. If a single campaign outgrows that, the next steps are sharded counters (N rows per
  coupon), a pool of pre-created tokens claimed with `FOR UPDATE SKIP LOCKED`, or a Redis counter
  reconciled with the database.
- **Geolocation at scale.** country.is allows 10 requests/s per IP, and all calls leave from the
  service's IP. In production: a country header from the CDN (e.g. `CF-IPCountry`), a local
  MaxMind or DB-IP database, or a self-hosted country.is. Any of them is a new adapter behind
  `CountryResolver`; nothing else changes. This also keeps IPs inside the company (GDPR).
- **Security.** Authentication was out of scope. In production `userId` comes from the access token
  (`sub`), not the body; creating coupons requires a role; rate limiting sits at the gateway.

## Assumptions

- Code, country, limit and validity period of a coupon never change, and coupons are not deleted.
  This is what makes the lock-free read and the fast path safe.
- A coupon cannot be created already expired; one that expires later stays readable.
- Uses are never given back. Releasing a use when an order is cancelled would need a compensating
  operation and would invalidate the "counter only grows" fast path.
- "Already redeemed" takes precedence over every other refusal for that user, because it is what
  actually stops them. A user who has not redeemed gets the first refusal in the order shown above.

## Next steps

- `CouponRedeemed` events through a transactional outbox to Kafka, for CRM and analytics.
- Coupon deactivation, and releasing a use on order cancellation (saga step).
- JVM AOT cache in the image (Java 25) for faster scale-out; the training run needs the database
  and Flyway disabled.

## How AI was used

<!-- Describe this in your own words: which tools, for what, and how you verified the result. -->
