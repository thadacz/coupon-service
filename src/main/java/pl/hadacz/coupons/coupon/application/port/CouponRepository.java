package pl.hadacz.coupons.coupon.application.port;

import java.time.Instant;
import java.util.Optional;

import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.UserId;

public interface CouponRepository {

    /// Stores a new coupon. Returns `false` when the code is already taken; the unique
    /// constraint decides, so two concurrent creations of the same code cannot both win.
    boolean tryCreate(Coupon coupon);

    Optional<Coupon> findByCode(CouponCode code);

    /// Atomically records that `userId` redeemed the coupon and consumes one use.
    /// Either both happen or neither does, also under any number of concurrent callers
    /// on any number of application instances. Expects the coupon to exist.
    RedemptionOutcome redeem(CouponCode code, UserId userId, Instant redeemedAt);
}
