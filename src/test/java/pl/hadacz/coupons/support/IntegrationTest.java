package pl.hadacz.coupons.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;

/// Full application context on a real PostgreSQL, with WireMock standing in for the
/// geolocation provider. All integration tests share one context, so Spring caches it
/// and the container starts once per test run. A real Tomcat on a random port, so end-to-end
/// tests exercise the same request pipeline as production (including RemoteIpValve).
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        // Concurrency tests start hundreds of virtual threads against a pool of 20 connections.
        "spring.datasource.hikari.connection-timeout=30000"
})
@AutoConfigureRestTestClient
@Import({PostgresTestcontainersConfiguration.class, GeoProviderStubConfiguration.class})
public @interface IntegrationTest {
}
