package pl.hadacz.coupons.coupon.application;

import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;

public sealed interface CreateCouponResult {

    record Created(Coupon coupon) implements CreateCouponResult {
    }

    record CodeAlreadyExists(CouponCode code) implements CreateCouponResult {
    }
}
