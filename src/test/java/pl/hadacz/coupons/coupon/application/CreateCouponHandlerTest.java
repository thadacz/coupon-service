package pl.hadacz.coupons.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pl.hadacz.coupons.coupon.application.CreateCouponResult.CodeAlreadyExists;
import pl.hadacz.coupons.coupon.application.CreateCouponResult.Created;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.ValidityPeriod;

class CreateCouponHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:15:30.123456789Z");

    private final InMemoryCouponRepository coupons = new InMemoryCouponRepository();
    private final CreateCouponHandler handler =
            new CreateCouponHandler(coupons, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("creates a coupon with no uses and a creation time truncated to microseconds")
    void createsCoupon() {
        var result = handler.handle(command("wiosna", "PL", 100));

        Coupon expected = new Coupon(CouponCode.of("WIOSNA"), CountryCode.of("PL"), 100, 0,
                ValidityPeriod.ALWAYS, Instant.parse("2026-11-24T10:15:30.123456Z"));
        assertThat(result).isEqualTo(new Created(expected));
        assertThat(coupons.findByCode(CouponCode.of("WIOSNA"))).contains(expected);
    }

    @Test
    @DisplayName("stores the validity period truncated to microseconds, like the creation time")
    void storesValidityPeriod() {
        var validity = new ValidityPeriod(Instant.parse("2026-12-01T00:00:00.000000999Z"), null);

        var result = handler.handle(new CreateCouponCommand(CouponCode.of("ZIMA"), CountryCode.of("PL"), 10, validity));

        assertThat(result).isInstanceOfSatisfying(Created.class, created -> assertThat(created.coupon().validity())
                .isEqualTo(new ValidityPeriod(Instant.parse("2026-12-01T00:00:00Z"), null)));
    }

    @Test
    @DisplayName("refuses a code that already exists in a different case")
    void refusesDuplicateInDifferentCase() {
        handler.handle(command("WIOSNA", "PL", 100));

        var result = handler.handle(command("Wiosna", "DE", 5));

        assertThat(result).isEqualTo(new CodeAlreadyExists(CouponCode.of("WIOSNA")));
        assertThat(coupons.findByCode(CouponCode.of("WIOSNA")))
                .map(Coupon::country)
                .contains(CountryCode.of("PL"));
    }

    private static CreateCouponCommand command(String code, String country, int maxUses) {
        return new CreateCouponCommand(
                CouponCode.of(code), CountryCode.of(country), maxUses, ValidityPeriod.ALWAYS);
    }
}
