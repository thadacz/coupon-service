package pl.hadacz.coupons.coupon.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unavailable;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unresolvable;
import pl.hadacz.coupons.coupon.application.port.CountryResolver;
import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.coupon.domain.RedemptionResult;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.AlreadyRedeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CouponNotFound;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Expired;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.GeolocationUnavailable;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.NotYetValid;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.ValidityPeriod;

/// Redeems a coupon. Checks run from cheapest to most expensive:
///
/// 1. read the coupon (no lock, no transaction; code, country, limit and dates never change),
/// 2. validity period, against the clock,
/// 3. fast path: a snapshot that says "exhausted" is final, because the counter only grows,
/// 4. resolve the client's country: a network call, so it happens before any transaction,
/// 5. atomic redemption in the database, which is the only authoritative check.
///
/// The clock is read once: the instant checked against the validity period is the one stored
/// as `redeemed_at`, so a recorded redemption never lies outside the coupon's period.
///
/// Consequence worth knowing: a user who already redeemed an exhausted or expired coupon gets
/// "limit reached" or "expired", not "already redeemed". All mean "you cannot use it", and the
/// order saves a call to the geolocation provider.
public final class RedeemCouponHandler {

    private final CouponRepository coupons;
    private final CountryResolver countries;
    private final Clock clock;

    public RedeemCouponHandler(CouponRepository coupons, CountryResolver countries, Clock clock) {
        this.coupons = coupons;
        this.countries = countries;
        this.clock = clock;
    }

    public RedemptionResult handle(RedeemCouponCommand command) {
        Optional<Coupon> found = coupons.findByCode(command.code());
        if (found.isEmpty()) {
            return new CouponNotFound(command.code());
        }
        Coupon coupon = found.get();
        Instant redeemedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ValidityPeriod validity = coupon.validity();
        if (validity.hasNotStartedAt(redeemedAt)) {
            return new NotYetValid(coupon.code(), validity.validFrom());
        }
        if (validity.hasEndedBy(redeemedAt)) {
            return new Expired(coupon.code(), validity.validUntil());
        }
        if (coupon.isExhausted()) {
            return new UsageLimitReached(coupon.code(), coupon.maxUses());
        }

        Optional<RedemptionResult> countryRejection = checkCountry(coupon, command.clientIp());
        if (countryRejection.isPresent()) {
            return countryRejection.get();
        }

        return switch (coupons.redeem(coupon.code(), command.userId(), redeemedAt)) {
            case REDEEMED -> new Redeemed(coupon.code(), command.userId(), redeemedAt);
            case ALREADY_REDEEMED -> new AlreadyRedeemed(coupon.code(), command.userId());
            case LIMIT_REACHED -> new UsageLimitReached(coupon.code(), coupon.maxUses());
        };
    }

    /// Fail closed: when the country cannot be verified, the coupon is not redeemed.
    private Optional<RedemptionResult> checkCountry(Coupon coupon, IpAddress clientIp) {
        return switch (countries.resolve(clientIp)) {
            case Resolved(var country) when coupon.isAvailableIn(country) -> Optional.empty();
            case Resolved(var country) ->
                    Optional.of(new CountryNotAllowed(coupon.code(), coupon.country(), country));
            case Unresolvable _ -> Optional.of(new CountryNotAllowed(coupon.code(), coupon.country(), null));
            case Unavailable _ -> Optional.of(new GeolocationUnavailable(coupon.code()));
        };
    }
}
