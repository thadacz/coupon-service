package pl.hadacz.coupons.coupon.adapter.geolocation;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import pl.hadacz.coupons.coupon.application.port.CountryResolution.Resolved;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unavailable;
import pl.hadacz.coupons.coupon.application.port.CountryResolution.Unresolvable;
import pl.hadacz.coupons.coupon.application.port.CountryResolver;
import pl.hadacz.coupons.coupon.domain.CountryCode;
import pl.hadacz.coupons.coupon.domain.IpAddress;
import pl.hadacz.coupons.support.IntegrationTest;

@IntegrationTest
class CountryIsCountryResolverTest {

    private static final IpAddress POLISH_IP = IpAddress.of("83.0.0.1");

    @Autowired
    CountryResolver resolver;

    @Autowired
    WireMockServer geoProvider;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void reset() {
        geoProvider.resetAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("maps the provider's answer to a country")
    void resolvesCountry() {
        geoProvider.stubFor(get("/83.0.0.1").willReturn(okJson("""
                {"ip": "83.0.0.1", "country": "PL"}
                """)));

        assertThat(resolver.resolve(POLISH_IP)).isEqualTo(new Resolved(CountryCode.of("PL")));
    }

    @Test
    @DisplayName("asks the provider once per IP and serves repeats from the cache")
    void cachesAnswers() {
        geoProvider.stubFor(get("/83.0.0.1").willReturn(okJson("""
                {"ip": "83.0.0.1", "country": "PL"}
                """)));

        resolver.resolve(POLISH_IP);
        resolver.resolve(POLISH_IP);

        geoProvider.verify(1, getRequestedFor(urlEqualTo("/83.0.0.1")));
    }

    @Test
    @DisplayName("treats 404 from the provider as an unknown country")
    void noData() {
        geoProvider.stubFor(get("/83.0.0.1").willReturn(aResponse().withStatus(404)));

        assertThat(resolver.resolve(POLISH_IP)).isEqualTo(new Unresolvable(Unresolvable.Reason.NO_DATA));
    }

    @Test
    @DisplayName("retries a 5xx twice, then reports the provider as unavailable and does not cache it")
    void retriesServerErrors() {
        geoProvider.stubFor(get("/83.0.0.1").willReturn(aResponse().withStatus(503)));

        assertThat(resolver.resolve(POLISH_IP)).isInstanceOf(Unavailable.class);
        geoProvider.verify(3, getRequestedFor(urlEqualTo("/83.0.0.1")));

        resolver.resolve(POLISH_IP);
        geoProvider.verify(6, getRequestedFor(urlEqualTo("/83.0.0.1")));
    }

    @Test
    @DisplayName("does not retry 429: the provider asked us to slow down")
    void rateLimited() {
        geoProvider.stubFor(get("/83.0.0.1").willReturn(aResponse().withStatus(429)));

        assertThat(resolver.resolve(POLISH_IP)).isInstanceOf(Unavailable.class);
        geoProvider.verify(1, getRequestedFor(urlEqualTo("/83.0.0.1")));
    }

    @Test
    @DisplayName("gives up on a slow provider after the read timeout")
    void slowProvider() {
        geoProvider.stubFor(get("/83.0.0.1").willReturn(okJson("""
                {"ip": "83.0.0.1", "country": "PL"}
                """).withFixedDelay(1_500)));

        assertThat(resolver.resolve(POLISH_IP)).isInstanceOf(Unavailable.class);
    }

    @Test
    @DisplayName("never sends private or loopback addresses to the provider")
    void skipsNonPublicAddresses() {
        assertThat(resolver.resolve(IpAddress.of("192.168.1.10")))
                .isEqualTo(new Unresolvable(Unresolvable.Reason.NON_PUBLIC_ADDRESS));
        assertThat(resolver.resolve(IpAddress.of("127.0.0.1")))
                .isEqualTo(new Unresolvable(Unresolvable.Reason.NON_PUBLIC_ADDRESS));

        assertThat(geoProvider.getAllServeEvents()).isEmpty();
    }
}
