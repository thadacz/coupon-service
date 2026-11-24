package pl.hadacz.coupons.coupon.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import pl.hadacz.coupons.coupon.application.port.CouponRepository;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.Redemption;
import pl.hadacz.coupons.coupon.domain.UserId;
import pl.hadacz.coupons.coupon.domain.ValidityPeriod;
import pl.hadacz.coupons.support.IntegrationTest;

@IntegrationTest
class JpaCouponRepositoryAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-24T10:00:00.123456Z");

    @Autowired
    CouponRepository coupons;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("DELETE FROM coupon_redemptions").update();
        jdbc.sql("DELETE FROM coupons").update();
    }

    @Test
    @DisplayName("stores a coupon and reads it back unchanged")
    void roundTrip() {
        Coupon coupon = Coupon.create(CouponCode.of("WIOSNA"), CountryCode.of("PL"), 100, CREATED_AT);

        assertThat(coupons.tryCreate(coupon)).isTrue();

        assertThat(coupons.findByCode(CouponCode.of("wiosna"))).contains(coupon);
    }

    @Test
    @DisplayName("stores the validity period, with open ends as NULL")
    void roundTripValidityPeriod() {
        Coupon bounded = Coupon.create(CouponCode.of("ZIMA"), CountryCode.of("PL"), 10,
                new ValidityPeriod(CREATED_AT, CREATED_AT.plusSeconds(3600)), CREATED_AT);
        Coupon openEnded = Coupon.create(CouponCode.of("LATO"), CountryCode.of("PL"), 10,
                new ValidityPeriod(CREATED_AT, null), CREATED_AT);

        coupons.tryCreate(bounded);
        coupons.tryCreate(openEnded);

        assertThat(coupons.findByCode(CouponCode.of("ZIMA"))).contains(bounded);
        assertThat(coupons.findByCode(CouponCode.of("LATO"))).contains(openEnded);
    }

    @Test
    @DisplayName("reports a taken code instead of throwing")
    void reportsTakenCode() {
        coupons.tryCreate(Coupon.create(CouponCode.of("LATO"), CountryCode.of("PL"), 10, CREATED_AT));

        boolean created = coupons.tryCreate(Coupon.create(CouponCode.of("lato"), CountryCode.of("DE"), 5, CREATED_AT));

        assertThat(created).isFalse();
        assertThat(coupons.findByCode(CouponCode.of("LATO"))).map(Coupon::maxUses).contains(10);
    }

    @Test
    @DisplayName("returns nothing for an unknown code")
    void unknownCode() {
        assertThat(coupons.findByCode(CouponCode.of("NIE-MA"))).isEmpty();
    }

    @Test
    @DisplayName("finds a redemption with its exact time, and only for the user who made it")
    void findsRedemption() {
        CouponCode code = CouponCode.of("WIOSNA");
        coupons.tryCreate(Coupon.create(code, CountryCode.of("PL"), 10, CREATED_AT));
        coupons.redeem(code, UserId.of("user-1"), CREATED_AT);

        assertThat(coupons.findRedemption(code, UserId.of("user-1")))
                .contains(new Redemption(code, UserId.of("user-1"), CREATED_AT));
        assertThat(coupons.findRedemption(code, UserId.of("user-2"))).isEmpty();
        assertThat(coupons.findRedemption(CouponCode.of("NIE-MA"), UserId.of("user-1"))).isEmpty();
    }
}
