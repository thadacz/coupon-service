package pl.hadacz.coupons.coupon.application.port;

import java.util.Optional;

import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;

public interface CouponRepository {

    /// Stores a new coupon. Returns `false` when the code is already taken; the unique
    /// constraint decides, so two concurrent creations of the same code cannot both win.
    boolean tryCreate(Coupon coupon);

    Optional<Coupon> findByCode(CouponCode code);
}
