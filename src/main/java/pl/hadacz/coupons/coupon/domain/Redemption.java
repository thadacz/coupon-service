package pl.hadacz.coupons.coupon.domain;

import java.time.Instant;
import java.util.Objects;

/// A recorded redemption: which user used which coupon, and when.
public record Redemption(CouponCode code, UserId userId, Instant redeemedAt) {

    public Redemption {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(redeemedAt, "redeemedAt");
    }
}
