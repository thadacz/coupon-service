package pl.hadacz.coupons.coupon.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.UserId;

class RedemptionMetricsTest {

    private static final CouponCode WIOSNA = CouponCode.of("WIOSNA");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RedemptionMetrics metrics = new RedemptionMetrics(registry);

    @Test
    @DisplayName("counts attempts per outcome, without the coupon code as a tag")
    void countsByOutcome() {
        metrics.record(new Redeemed(WIOSNA, UserId.of("user-1"), Instant.parse("2026-11-24T10:00:00Z")));
        metrics.record(new UsageLimitReached(WIOSNA, 1));
        metrics.record(new UsageLimitReached(WIOSNA, 1));
        metrics.record(new CountryNotAllowed(WIOSNA, CountryCode.of("PL"), null));

        assertThat(count("redeemed")).isEqualTo(1);
        assertThat(count("usage_limit_reached")).isEqualTo(2);
        assertThat(count("country_not_allowed")).isEqualTo(1);
        assertThat(registry.get("coupons.redemptions").counters())
                .allSatisfy(counter -> assertThat(counter.getId().getTags()).hasSize(1));
    }

    private double count(String outcome) {
        return registry.get("coupons.redemptions").tag("outcome", outcome).counter().count();
    }
}
