package pl.hadacz.coupons.coupon.adapter.web;

import jakarta.validation.constraints.NotBlank;

/// The client IP is not part of the body: it is taken from the connection,
/// after Tomcat has applied X-Forwarded-For from trusted proxies only.
record RedeemCouponRequest(@NotBlank String userId) {
}
