package pl.hadacz.coupons.coupon.application;

import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.coupon.domain.UserId;

public record RedeemCouponCommand(CouponCode code, UserId userId, IpAddress clientIp) {
}
