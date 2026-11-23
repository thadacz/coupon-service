package pl.hadacz.coupons.coupon.adapter.web;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import pl.hadacz.coupons.coupon.domain.RedemptionResult;

/// `coupons.redemptions{outcome=...}`: how many attempts end in each way. A rising
/// `geolocation_unavailable` or `usage_limit_reached` is visible on a dashboard before
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

    private static String outcomeTag(RedemptionResult result) {
        // UsageLimitReached -> usage_limit_reached
        return result.getClass().getSimpleName()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
