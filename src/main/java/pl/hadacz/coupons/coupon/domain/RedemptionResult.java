package pl.hadacz.coupons.coupon.domain;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/// Every way a redemption attempt can end. Expected business outcomes are values, not
/// exceptions: callers handle them with an exhaustive `switch`, so a new outcome does not
/// compile until every caller (e.g. the HTTP mapping) handles it.
public sealed interface RedemptionResult {

    CouponCode code();

    record Redeemed(CouponCode code, UserId userId, Instant redeemedAt) implements RedemptionResult {
    }

    record CouponNotFound(CouponCode code) implements RedemptionResult {
    }

    record NotYetValid(CouponCode code, Instant validFrom) implements RedemptionResult {
    }

    record Expired(CouponCode code, Instant validUntil) implements RedemptionResult {
    }

    record UsageLimitReached(CouponCode code, int maxUses) implements RedemptionResult {
    }

    /// `detectedCountry` is null when the client's country could not be determined.
    record CountryNotAllowed(CouponCode code, CountryCode requiredCountry, @Nullable CountryCode detectedCountry)
            implements RedemptionResult {
    }

    record AlreadyRedeemed(CouponCode code, UserId userId) implements RedemptionResult {
    }

    record GeolocationUnavailable(CouponCode code) implements RedemptionResult {
    }
}
