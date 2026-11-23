package pl.hadacz.coupons.coupon.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/// Shape of the request only. Domain rules (code format, ISO country) are checked by
/// the value objects, so they are written down in exactly one place.
record CreateCouponRequest(
        @NotBlank String code,
        @NotNull @Positive Integer maxUses,
        @NotBlank String country) {
}
