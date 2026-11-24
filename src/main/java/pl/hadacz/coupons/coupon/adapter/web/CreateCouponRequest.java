package pl.hadacz.coupons.coupon.adapter.web;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

/// Shape of the request only. Domain rules (code format, ISO country, a validity period that
/// ends after it starts) are checked by the value objects, so they are written down in exactly one place.
record CreateCouponRequest(
        @NotBlank String code,
        @NotNull @Positive Integer maxUses,
        @NotBlank String country,
        @Nullable Instant validFrom,
        @Nullable Instant validUntil) {
}
