package pl.hadacz.coupons.coupon.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.application.port.RedemptionOutcome;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.UserId;
import pl.hadacz.coupons.support.Concurrently;
import pl.hadacz.coupons.support.IntegrationTest;

/// The core guarantee of the service, checked on a real PostgreSQL: under a burst of
/// concurrent attempts the limit is never exceeded and each user redeems at most once.
/// A test can only raise confidence; the guarantee itself comes from the SQL (see the adapter).
@IntegrationTest
class CouponRedemptionConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final CouponCode CODE = CouponCode.of("WIOSNA");

    @Autowired
    CouponRepository coupons;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM coupon_redemptions").update();
        jdbc.sql("DELETE FROM coupons").update();
    }

    @RepeatedTest(3)
    @DisplayName("500 users racing for 50 uses: exactly 50 win")
    void neverExceedsLimit() throws Exception {
        createCoupon(50);

        List<RedemptionOutcome> outcomes = Concurrently.run(500,
                i -> coupons.redeem(CODE, UserId.of("user-" + i), NOW));

        assertThat(count(outcomes)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RedemptionOutcome.REDEEMED, 50L,
                RedemptionOutcome.LIMIT_REACHED, 450L));
        assertThat(currentUses()).isEqualTo(50);
        assertThat(redemptionRows()).isEqualTo(50);
    }

    @RepeatedTest(3)
    @DisplayName("one user firing 50 parallel requests redeems once")
    void oneRedemptionPerUser() throws Exception {
        createCoupon(10);

        List<RedemptionOutcome> outcomes = Concurrently.run(50,
                i -> coupons.redeem(CODE, UserId.of("user-1"), NOW));

        assertThat(count(outcomes)).containsExactlyInAnyOrderEntriesOf(Map.of(
                RedemptionOutcome.REDEEMED, 1L,
                RedemptionOutcome.ALREADY_REDEEMED, 49L));
        assertThat(currentUses()).isEqualTo(1);
    }

    @Test
    @DisplayName("an attempt over the limit leaves no redemption row behind")
    void limitReachedRollsBackInsert() {
        createCoupon(1);
        coupons.redeem(CODE, UserId.of("user-1"), NOW);

        RedemptionOutcome outcome = coupons.redeem(CODE, UserId.of("user-2"), NOW);

        assertThat(outcome).isEqualTo(RedemptionOutcome.LIMIT_REACHED);
        assertThat(redemptionRows()).isEqualTo(1);
        assertThat(currentUses()).isEqualTo(1);
    }

    @Test
    @DisplayName("a repeated attempt by the same user does not consume a use")
    void repeatedAttemptDoesNotConsume() {
        createCoupon(5);
        coupons.redeem(CODE, UserId.of("user-1"), NOW);

        RedemptionOutcome outcome = coupons.redeem(CODE, UserId.of("user-1"), NOW);

        assertThat(outcome).isEqualTo(RedemptionOutcome.ALREADY_REDEEMED);
        assertThat(currentUses()).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("WIOSNA, wiosna and Wiosna created at once: exactly one succeeds")
    void caseInsensitiveCodeUnderRace() throws Exception {
        List<String> variants = List.of("WIOSNA", "wiosna", "Wiosna", "wIOSNA", "WiOsNa");

        List<Boolean> created = Concurrently.run(variants.size(), i -> coupons.tryCreate(
                Coupon.create(CouponCode.of(variants.get(i)), CountryCode.of("PL"), 10, NOW)));

        assertThat(created).containsOnlyOnce(true);
    }

    private void createCoupon(int maxUses) {
        coupons.tryCreate(Coupon.create(CODE, CountryCode.of("PL"), maxUses, NOW));
    }

    private int currentUses() {
        return jdbc.sql("SELECT current_uses FROM coupons WHERE code = :code")
                .param("code", CODE.value()).query(Integer.class).single();
    }

    private long redemptionRows() {
        return jdbc.sql("SELECT count(*) FROM coupon_redemptions").query(Long.class).single();
    }

    private static Map<RedemptionOutcome, Long> count(List<RedemptionOutcome> outcomes) {
        return outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
