package pl.hadacz.coupons.coupon.adapter.web;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import pl.hadacz.coupons.coupon.domain.Coupon;

/// `validFrom` and `validUntil` are null when the period is open on that side.
record CouponResponse(String code, String country, int maxUses, int currentUses,
                      @Nullable Instant validFrom, @Nullable Instant validUntil, Instant createdAt) {

    static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.code().value(),
                coupon.country().value(),
                coupon.maxUses(),
                coupon.currentUses(),
                coupon.validity().validFrom(),
                coupon.validity().validUntil(),
                coupon.createdAt());
    }
}
