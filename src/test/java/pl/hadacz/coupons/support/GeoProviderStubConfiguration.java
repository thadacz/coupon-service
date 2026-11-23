package pl.hadacz.coupons.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/// WireMock in place of api.country.is for every integration test, so tests never depend on
/// the real provider and can simulate its failures (5xx, 429, slow responses).
@TestConfiguration(proxyBeanMethods = false)
public class GeoProviderStubConfiguration {

    @Bean(destroyMethod = "stop")
    WireMockServer geoProvider() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @Bean
    DynamicPropertyRegistrar geoProviderUrl(WireMockServer geoProvider) {
        return registry -> registry.add("spring.http.serviceclient.geo.base-url", geoProvider::baseUrl);
    }
}
