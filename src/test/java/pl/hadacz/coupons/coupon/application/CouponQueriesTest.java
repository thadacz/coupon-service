package pl.hadacz.coupons.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.Redemption;
import pl.hadacz.coupons.coupon.domain.UserId;

class CouponQueriesTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final CouponCode WIOSNA = CouponCode.of("WIOSNA");

    private final InMemoryCouponRepository coupons = new InMemoryCouponRepository();
    private final CouponQueries queries = new CouponQueries(coupons);

    @Test
    @DisplayName("finds a redemption only for the user who made it")
    void findsRedemption() {
        coupons.save(Coupon.create(WIOSNA, CountryCode.of("PL"), 10, NOW));
        coupons.redeem(WIOSNA, UserId.of("user-1"), NOW);

        assertThat(queries.findRedemption(WIOSNA, UserId.of("user-1")))
                .contains(new Redemption(WIOSNA, UserId.of("user-1"), NOW));
        assertThat(queries.findRedemption(WIOSNA, UserId.of("user-2"))).isEmpty();
    }
}
