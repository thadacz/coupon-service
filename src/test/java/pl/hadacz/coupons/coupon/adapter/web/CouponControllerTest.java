package pl.hadacz.coupons.coupon.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import pl.hadacz.coupons.coupon.application.CouponQueries;
import pl.hadacz.coupons.coupon.application.CreateCouponHandler;
import pl.hadacz.coupons.coupon.application.CreateCouponResult.CodeAlreadyExists;
import pl.hadacz.coupons.coupon.application.CreateCouponResult.Created;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.Coupon;
import pl.hadacz.coupons.coupon.domain.CouponCode;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    private static final Coupon WIOSNA = Coupon.create(CouponCode.of("WIOSNA"), CountryCode.of("PL"), 100,
            Instant.parse("2026-11-24T10:00:00Z"));

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    CreateCouponHandler createCoupon;

    @MockitoBean
    CouponQueries queries;

    @Test
    @DisplayName("POST /coupons returns 201 with Location and the normalized coupon")
    void createsCoupon() {
        given(createCoupon.handle(any())).willReturn(new Created(WIOSNA));

        assertThat(mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "wiosna", "maxUses": 100, "country": "pl"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/v1/coupons/WIOSNA")
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"code": "WIOSNA", "country": "PL", "maxUses": 100, "currentUses": 0,
                         "createdAt": "2026-11-24T10:00:00Z"}
                        """);
    }

    @Test
    @DisplayName("POST /coupons returns 409 problem when the code exists")
    void rejectsDuplicate() {
        given(createCoupon.handle(any())).willReturn(new CodeAlreadyExists(CouponCode.of("WIOSNA")));

        assertThat(mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "WIOSNA", "maxUses": 100, "country": "PL"}
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 409, "errorCode": "COUPON_CODE_ALREADY_EXISTS",
                         "type": "urn:problem-type:coupon-code-already-exists"}
                        """);
    }

    @Test
    @DisplayName("POST /coupons lists every invalid field")
    void listsInvalidFields() {
        assertThat(mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "", "maxUses": 0}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("code", "maxUses", "country");
    }

    @Test
    @DisplayName("POST /coupons rejects a country that is not an ISO code")
    void rejectsUnknownCountry() {
        assertThat(mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "WIOSNA", "maxUses": 10, "country": "XX"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"errorCode": "VALIDATION_FAILED", "errors": [{"field": "country"}]}
                        """);
    }

    @Test
    @DisplayName("POST /coupons answers malformed JSON with a problem, not a stack trace")
    void rejectsMalformedJson() {
        assertThat(mvc.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("GET /coupons/{code} finds a coupon regardless of the case of the code")
    void getsCouponCaseInsensitively() {
        given(queries.findByCode(CouponCode.of("WIOSNA"))).willReturn(Optional.of(WIOSNA));

        assertThat(mvc.get().uri("/api/v1/coupons/wiosna"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("WIOSNA");
    }

    @Test
    @DisplayName("GET /coupons/{code} returns 404 problem for an unknown code")
    void returnsNotFound() {
        given(queries.findByCode(any())).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/v1/coupons/NIE-MA"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.errorCode")
                .isEqualTo("COUPON_NOT_FOUND");
    }
}
