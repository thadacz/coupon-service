package pl.hadacz.coupons;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import pl.hadacz.coupons.support.IntegrationTest;

/// Whole stack over HTTP: Tomcat (with RemoteIpValve), controllers, use cases, PostgreSQL,
/// and the geolocation client talking to a WireMock stand-in for api.country.is.
@IntegrationTest
class CouponApiEndToEndTest {

    private static final String POLISH_IP = "83.0.0.1";
    private static final String GERMAN_IP = "85.0.0.1";

    @Autowired
    RestTestClient client;

    @Autowired
    WireMockServer geoProvider;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM coupon_redemptions").update();
        jdbc.sql("DELETE FROM coupons").update();
        geoProvider.resetAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        geoProvider.stubFor(get("/" + POLISH_IP).willReturn(okJson("""
                {"ip": "83.0.0.1", "country": "PL"}
                """)));
        geoProvider.stubFor(get("/" + GERMAN_IP).willReturn(okJson("""
                {"ip": "85.0.0.1", "country": "DE"}
                """)));
    }

    @Test
    @DisplayName("create, redeem from Poland, see the counter grow, and get refused a second time")
    void happyPath() {
        createCoupon("wiosna", 2, "PL");

        redeem("WIOSNA", "user-1", POLISH_IP)
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/v1/coupons/WIOSNA/redemptions/user-1")
                .expectBody().jsonPath("$.couponCode").isEqualTo("WIOSNA");

        client.get().uri("/api/v1/coupons/Wiosna").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.currentUses").isEqualTo(1);

        client.get().uri("/api/v1/coupons/wiosna/redemptions/user-1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.userId").isEqualTo("user-1");
        client.get().uri("/api/v1/coupons/WIOSNA/redemptions/user-2").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.errorCode").isEqualTo("REDEMPTION_NOT_FOUND");

        redeem("wiosna", "user-1", POLISH_IP)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("COUPON_ALREADY_REDEEMED");
    }

    @Test
    @DisplayName("the last use goes to one user, the next one hits the limit")
    void limit() {
        createCoupon("LATO", 1, "PL");

        redeem("LATO", "user-1", POLISH_IP).expectStatus().isCreated();
        redeem("LATO", "user-2", POLISH_IP)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("COUPON_USAGE_LIMIT_REACHED");
        // The user who took the last use is told they already redeemed it, not that it ran out.
        redeem("LATO", "user-1", POLISH_IP)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("COUPON_ALREADY_REDEEMED");
    }

    @Test
    @DisplayName("a request from Germany cannot redeem a Polish coupon")
    void otherCountry() {
        createCoupon("WIOSNA", 10, "PL");

        redeem("WIOSNA", "user-1", GERMAN_IP)
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.errorCode").isEqualTo("COUPON_COUNTRY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("a coupon whose validity starts in the future cannot be redeemed yet")
    void notYetValid() {
        client.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "ZIMA", "maxUses", 10, "country", "PL", "validFrom", "2999-01-01T00:00:00Z"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.validFrom").isEqualTo("2999-01-01T00:00:00Z");

        redeem("ZIMA", "user-1", POLISH_IP)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("COUPON_NOT_YET_VALID");
    }

    @Test
    @DisplayName("without X-Forwarded-For the client is localhost, whose country is unknown")
    void loopbackClient() {
        createCoupon("WIOSNA", 10, "PL");

        client.post().uri("/api/v1/coupons/WIOSNA/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", "user-1"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("the same code in another case cannot be created twice")
    void duplicateCode() {
        createCoupon("WIOSNA", 10, "PL");

        client.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "wiosna", "maxUses", 5, "country", "DE"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("COUPON_CODE_ALREADY_EXISTS");
    }

    private void createCoupon(String code, int maxUses, String country) {
        client.post().uri("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", code, "maxUses", maxUses, "country", country))
                .exchange()
                .expectStatus().isCreated();
    }

    // The test client connects from 127.0.0.1, a trusted proxy by default,
    // so Tomcat takes the client address from X-Forwarded-For, as behind a load balancer.
    private RestTestClient.ResponseSpec redeem(String code, String userId, String clientIp) {
        return client.post().uri("/api/v1/coupons/{code}/redemptions", code)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", userId))
                .exchange();
    }
}
