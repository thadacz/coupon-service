package pl.hadacz.coupons.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pl.hadacz.coupons.coupon.application.port.CountryResolution;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unavailable;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unresolvable;
import pl.hadacz.coupons.coupon.application.port.CountryResolver;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.AlreadyRedeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CouponNotFound;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Expired;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.GeolocationUnavailable;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.NotYetValid;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.UserId;
import pl.hadacz.coupons.coupon.domain.ValidityPeriod;

class RedeemCouponHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final CouponCode WIOSNA = CouponCode.of("WIOSNA");
    private static final CountryCode PL = CountryCode.of("PL");
    private static final IpAddress CLIENT_IP = IpAddress.of("83.0.0.1");
    private static final UserId USER = UserId.of("user-42");

    private final InMemoryCouponRepository coupons = new InMemoryCouponRepository();
    private final StubCountryResolver countries = new StubCountryResolver();
    private final RedeemCouponHandler handler =
            new RedeemCouponHandler(coupons, countries, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void polishClientByDefault() {
        countries.answer = new Resolved(PL);
    }

    @Test
    @DisplayName("redeems a coupon for a client from the coupon's country")
    void redeems() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, NOW));

        assertThat(handler.handle(command(USER))).isEqualTo(new Redeemed(WIOSNA, USER, NOW));
        assertThat(coupons.findByCode(WIOSNA)).map(Coupon::currentUses).contains(1);
    }

    @Test
    @DisplayName("reports an unknown code without asking for the country")
    void unknownCoupon() {
        assertThat(handler.handle(command(USER))).isEqualTo(new CouponNotFound(WIOSNA));
        assertThat(countries.calls).hasValue(0);
    }

    @Test
    @DisplayName("rejects a coupon before its validity starts, without asking for the country")
    void notYetValid() {
        Instant start = NOW.plusSeconds(1);
        coupons.save(Coupon.create(WIOSNA, PL, 10, new ValidityPeriod(start, null), NOW.minusSeconds(60)));

        assertThat(handler.handle(command(USER))).isEqualTo(new NotYetValid(WIOSNA, start));
        assertThat(countries.calls).hasValue(0);
    }

    @Test
    @DisplayName("rejects a coupon from the instant its validity ends, without asking for the country")
    void expired() {
        coupons.save(new Coupon(WIOSNA, PL, 10, 0, new ValidityPeriod(null, NOW), NOW.minusSeconds(60)));

        assertThat(handler.handle(command(USER))).isEqualTo(new Expired(WIOSNA, NOW));
        assertThat(countries.calls).hasValue(0);
    }

    @Test
    @DisplayName("redeems from the very instant the validity starts")
    void validFromStart() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, new ValidityPeriod(NOW, NOW.plusSeconds(1)), NOW.minusSeconds(60)));

        assertThat(handler.handle(command(USER))).isEqualTo(new Redeemed(WIOSNA, USER, NOW));
    }

    @Test
    @DisplayName("rejects an exhausted coupon without asking for the country")
    void exhaustedFastPath() {
        coupons.save(new Coupon(WIOSNA, PL, 2, 2, ValidityPeriod.ALWAYS, NOW));

        assertThat(handler.handle(command(USER))).isEqualTo(new UsageLimitReached(WIOSNA, 2));
        assertThat(countries.calls).hasValue(0);
    }

    @Test
    @DisplayName("rejects a client from another country")
    void otherCountry() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, NOW));
        countries.answer = new Resolved(CountryCode.of("DE"));

        assertThat(handler.handle(command(USER)))
                .isEqualTo(new CountryNotAllowed(WIOSNA, PL, CountryCode.of("DE")));
        assertThat(coupons.findByCode(WIOSNA)).map(Coupon::currentUses).contains(0);
    }

    @Test
    @DisplayName("rejects a client whose country cannot be determined")
    void unknownCountry() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, NOW));
        countries.answer = new Unresolvable(Unresolvable.Reason.NO_DATA);

        assertThat(handler.handle(command(USER))).isEqualTo(new CountryNotAllowed(WIOSNA, PL, null));
    }

    @Test
    @DisplayName("fails closed when the geolocation provider is unavailable")
    void providerUnavailable() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, NOW));
        countries.answer = new Unavailable("timeout");

        assertThat(handler.handle(command(USER))).isEqualTo(new GeolocationUnavailable(WIOSNA));
        assertThat(coupons.findByCode(WIOSNA)).map(Coupon::currentUses).contains(0);
    }

    @Test
    @DisplayName("lets each user redeem a coupon only once")
    void onlyOncePerUser() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, NOW));
        handler.handle(command(USER));

        assertThat(handler.handle(command(USER))).isEqualTo(new AlreadyRedeemed(WIOSNA, USER));
    }

    @Test
    @DisplayName("tells the user who took the last use that they already redeemed it, not that it ran out")
    void alreadyRedeemedRatherThanExhausted() {
        coupons.save(Coupon.create(WIOSNA, PL, 1, NOW));
        handler.handle(command(USER));

        assertThat(handler.handle(command(USER))).isEqualTo(new AlreadyRedeemed(WIOSNA, USER));
        assertThat(countries.calls).hasValue(1);
    }

    @Test
    @DisplayName("tells a user who already redeemed so, also after the coupon expired or from another country")
    void alreadyRedeemedWinsOverOtherRejections() {
        coupons.save(Coupon.create(WIOSNA, PL, 10, NOW));
        handler.handle(command(USER));

        countries.answer = new Resolved(CountryCode.of("DE"));
        assertThat(handler.handle(command(USER))).isEqualTo(new AlreadyRedeemed(WIOSNA, USER));

        coupons.save(new Coupon(WIOSNA, PL, 10, 1, new ValidityPeriod(null, NOW), NOW.minusSeconds(60)));
        assertThat(handler.handle(command(USER))).isEqualTo(new AlreadyRedeemed(WIOSNA, USER));
    }

    @Test
    @DisplayName("reports the limit when the last use goes to someone else first")
    void limitReachedInRepository() {
        coupons.save(Coupon.create(WIOSNA, PL, 1, NOW));
        handler.handle(command(UserId.of("first")));

        assertThat(handler.handle(command(UserId.of("second")))).isEqualTo(new UsageLimitReached(WIOSNA, 1));
    }

    private static RedeemCouponCommand command(UserId userId) {
        return new RedeemCouponCommand(WIOSNA, userId, CLIENT_IP);
    }

    private static final class StubCountryResolver implements CountryResolver {

        private final AtomicInteger calls = new AtomicInteger();
        private CountryResolution answer = new Unavailable("not configured");

        @Override
        public CountryResolution resolve(IpAddress ip) {
            calls.incrementAndGet();
            return answer;
        }
    }
}
