package pl.hadacz.coupons.coupon.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import pl.hadacz.coupons.coupon.application.RedeemCouponCommand;
import pl.hadacz.coupons.coupon.application.RedeemCouponHandler;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.CouponCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.coupon.domain.RedemptionResult;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.AlreadyRedeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CountryNotAllowed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.CouponNotFound;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.GeolocationUnavailable;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.Redeemed;
import pl.hadacz.coupons.coupon.domain.RedemptionResult.UsageLimitReached;
import pl.hadacz.coupons.coupon.domain.UserId;

@WebMvcTest(RedemptionController.class)
class RedemptionControllerTest {

    private static final CouponCode WIOSNA = CouponCode.of("WIOSNA");
    private static final UserId USER = UserId.of("user-42");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    RedeemCouponHandler redeemCoupon;

    @Test
    @DisplayName("201 with the redemption, built from the normalized code and the connection's IP")
    void redeemed() {
        given(redeemCoupon.handle(any()))
                .willReturn(new Redeemed(WIOSNA, USER, Instant.parse("2026-11-24T10:00:00Z")));

        assertThat(redeem("wiosna", "83.0.0.1"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"couponCode": "WIOSNA", "userId": "user-42", "redeemedAt": "2026-11-24T10:00:00Z"}
                        """);
        then(redeemCoupon).should()
                .handle(new RedeemCouponCommand(WIOSNA, USER, IpAddress.of("83.0.0.1")));
    }

    @Test
    @DisplayName("404 COUPON_NOT_FOUND")
    void notFound() {
        expectProblem(new CouponNotFound(WIOSNA), HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND");
    }

    @Test
    @DisplayName("409 COUPON_USAGE_LIMIT_REACHED")
    void limitReached() {
        expectProblem(new UsageLimitReached(WIOSNA, 100), HttpStatus.CONFLICT, "COUPON_USAGE_LIMIT_REACHED");
    }

    @Test
    @DisplayName("409 COUPON_ALREADY_REDEEMED")
    void alreadyRedeemed() {
        expectProblem(new AlreadyRedeemed(WIOSNA, USER), HttpStatus.CONFLICT, "COUPON_ALREADY_REDEEMED");
    }

    @Test
    @DisplayName("403 COUPON_COUNTRY_NOT_ALLOWED, for another or an unknown country")
    void countryNotAllowed() {
        expectProblem(new CountryNotAllowed(WIOSNA, CountryCode.of("PL"), CountryCode.of("DE")),
                HttpStatus.FORBIDDEN, "COUPON_COUNTRY_NOT_ALLOWED");
        expectProblem(new CountryNotAllowed(WIOSNA, CountryCode.of("PL"), null),
                HttpStatus.FORBIDDEN, "COUPON_COUNTRY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("503 GEOLOCATION_UNAVAILABLE with Retry-After")
    void geolocationUnavailable() {
        given(redeemCoupon.handle(any())).willReturn(new GeolocationUnavailable(WIOSNA));

        assertThat(redeem("WIOSNA", "83.0.0.1"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasHeader("Retry-After", "5")
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("GEOLOCATION_UNAVAILABLE");
    }

    @Test
    @DisplayName("400 when userId is missing")
    void missingUserId() {
        assertThat(mvc.post().uri("/api/v1/coupons/WIOSNA/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("userId");
    }

    private void expectProblem(RedemptionResult result, HttpStatus status, String errorCode) {
        given(redeemCoupon.handle(any())).willReturn(result);

        assertThat(redeem("WIOSNA", "83.0.0.1"))
                .hasStatus(status)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo(errorCode);
    }

    private MvcTestResult redeem(String code, String remoteAddress) {
        return mvc.post().uri("/api/v1/coupons/{code}/redemptions", code)
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId": "user-42"}
                        """)
                .exchange();
    }
}
