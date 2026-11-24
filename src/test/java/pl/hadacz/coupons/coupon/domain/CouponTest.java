package pl.hadacz.coupons.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouponTest {

    private static final CouponCode CODE = CouponCode.of("WIOSNA");
    private static final CountryCode PL = CountryCode.of("PL");
    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    @DisplayName("a new coupon starts with no uses")
    void newCouponHasNoUses() {
        Coupon coupon = Coupon.create(CODE, PL, 100, NOW);

        assertThat(coupon.currentUses()).isZero();
        assertThat(coupon.isExhausted()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("requires a positive usage limit")
    void requiresPositiveLimit(int maxUses) {
        assertThatThrownBy(() -> Coupon.create(CODE, PL, maxUses, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("is exhausted when the counter reaches the limit")
    void exhaustedAtLimit() {
        assertThat(new Coupon(CODE, PL, 2, 1, ValidityPeriod.ALWAYS, NOW).isExhausted()).isFalse();
        assertThat(new Coupon(CODE, PL, 2, 2, ValidityPeriod.ALWAYS, NOW).isExhausted()).isTrue();
    }

    @Test
    @DisplayName("never holds a counter outside 0..limit")
    void counterWithinLimit() {
        assertThatThrownBy(() -> new Coupon(CODE, PL, 2, 3, ValidityPeriod.ALWAYS, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coupon(CODE, PL, 2, -1, ValidityPeriod.ALWAYS, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("is available only in its own country")
    void availableOnlyInItsCountry() {
        Coupon coupon = Coupon.create(CODE, PL, 10, NOW);

        assertThat(coupon.isAvailableIn(CountryCode.of("PL"))).isTrue();
        assertThat(coupon.isAvailableIn(CountryCode.of("DE"))).isFalse();
    }

    @Test
    @DisplayName("cannot be created already expired, but a stored one may have expired since")
    void notCreatedExpired() {
        ValidityPeriod endedNow = new ValidityPeriod(null, NOW);

        assertThatThrownBy(() -> Coupon.create(CODE, PL, 10, endedNow, NOW))
                .isInstanceOf(DomainValidationException.class);
        assertThat(new Coupon(CODE, PL, 10, 0, endedNow, NOW.minusSeconds(60)).validity()).isEqualTo(endedNow);
    }
}
