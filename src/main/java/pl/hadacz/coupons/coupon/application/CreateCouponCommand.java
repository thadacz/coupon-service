package pl.hadacz.coupons.coupon.application;

import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.CouponCode;

public record CreateCouponCommand(CouponCode code, CountryCode country, int maxUses) {
}
