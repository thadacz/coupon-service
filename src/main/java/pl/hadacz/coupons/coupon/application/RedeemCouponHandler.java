package pl.hadacz.coupons.coupon.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
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
import pl.hadacz.coupons.coupon.domain.RedemptionResult.NotYetValid;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.UserId;
import pl.hadacz.coupons.coupon.domain.ValidityPeriod;

/// Redeems a coupon. Every check that can refuse runs before the transaction:
///
/// 1. read the coupon (no lock, no transaction; code, country, limit and dates never change),
/// 2. validity period, against the clock,
/// 3. fast path: a snapshot that says "exhausted" is final, because the counter only grows,
/// 4. the client's country, looked up in the local IP database,
/// 5. atomic redemption in the database, which is the only authoritative check.
///
/// The clock is read once: the instant checked against the validity period is the one stored
/// as `redeemed_at`, so a recorded redemption never lies outside the coupon's period.
///
/// A user who has already redeemed the coupon is always told so, even when the coupon has
/// since run out, expired or is requested from another country: that is what actually stops
/// them. The lookup runs only on the refusal path, so a successful redemption costs nothing extra.
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

        Optional<RedemptionResult> rejection = checkBeforeRedeeming(coupon, command.clientIp(), redeemedAt);
        if (rejection.isPresent()) {
            return previousRedemptionBy(coupon, command.userId()).orElse(rejection.get());
        }

        return switch (coupons.redeem(coupon.code(), command.userId(), redeemedAt)) {
            case REDEEMED -> new Redeemed(coupon.code(), command.userId(), redeemedAt);
            case ALREADY_REDEEMED -> new AlreadyRedeemed(coupon.code(), command.userId());
            case LIMIT_REACHED -> new UsageLimitReached(coupon.code(), coupon.maxUses());
        };
    }

    /// Everything that can refuse a redemption before the transaction, cheapest first.
    private Optional<RedemptionResult> checkBeforeRedeeming(Coupon coupon, IpAddress clientIp, Instant now) {
        ValidityPeriod validity = coupon.validity();
        if (validity.hasNotStartedAt(now)) {
            return Optional.of(new NotYetValid(coupon.code(), validity.validFrom()));
        }
        if (validity.hasEndedBy(now)) {
            return Optional.of(new Expired(coupon.code(), validity.validUntil()));
        }
        if (coupon.isExhausted()) {
            return Optional.of(new UsageLimitReached(coupon.code(), coupon.maxUses()));
        }
        return checkCountry(coupon, clientIp);
    }

    private Optional<RedemptionResult> previousRedemptionBy(Coupon coupon, UserId userId) {
        return coupons.findRedemption(coupon.code(), userId)
                .map(redemption -> new AlreadyRedeemed(coupon.code(), userId));
    }

    /// Fail closed: when the country cannot be determined, the coupon is not redeemed.
    private Optional<RedemptionResult> checkCountry(Coupon coupon, IpAddress clientIp) {
        return switch (countries.resolve(clientIp)) {
            case Resolved(var country) when coupon.isAvailableIn(country) -> Optional.empty();
            case Resolved(var country) ->
                    Optional.of(new CountryNotAllowed(coupon.code(), coupon.country(), country));
            case Unresolvable _ -> Optional.of(new CountryNotAllowed(coupon.code(), coupon.country(), null));
        };
    }
}
