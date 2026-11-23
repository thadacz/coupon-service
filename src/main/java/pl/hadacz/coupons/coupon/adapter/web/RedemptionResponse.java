package pl.hadacz.coupons.coupon.adapter.web;

import java.time.Instant;

import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;

record RedemptionResponse(String couponCode, String userId, Instant redeemedAt) {

    static RedemptionResponse from(Redeemed redeemed) {
        return new RedemptionResponse(redeemed.code().value(), redeemed.userId().value(), redeemed.redeemedAt());
    }
}
