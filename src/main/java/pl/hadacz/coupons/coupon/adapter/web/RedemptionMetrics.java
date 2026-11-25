package pl.hadacz.coupons.coupon.adapter.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import pl.hadacz.coupons.coupon.domain.RedemptionResult;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.AlreadyRedeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CouponNotFound;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Expired;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.NotYetValid;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;

/// `coupons.redemptions{outcome=...}`: how many attempts end in each way. A rising
/// `country_not_allowed` or `usage_limit_reached` is visible on a dashboard before
/// anyone files a ticket. The coupon code is not a tag: unbounded tag values blow up metrics.
@Component
class RedemptionMetrics {

    private final MeterRegistry registry;

    RedemptionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void record(RedemptionResult result) {
        Counter.builder("coupons.redemptions")
                .description("Coupon redemption attempts by outcome")
                .tag("outcome", outcomeTag(result))
                .register(registry)
                .increment();
    }

    /// Exhaustive, like the HTTP mapping: a new outcome does not compile until it has a tag.
    /// The tags are part of the dashboards, so they are spelled out rather than derived from class names.
    private static String outcomeTag(RedemptionResult result) {
        return switch (result) {
            case Redeemed _ -> "redeemed";
            case CouponNotFound _ -> "coupon_not_found";
            case NotYetValid _ -> "not_yet_valid";
            case Expired _ -> "expired";
            case UsageLimitReached _ -> "usage_limit_reached";
            case CountryNotAllowed _ -> "country_not_allowed";
            case AlreadyRedeemed _ -> "already_redeemed";
        };
    }
}
