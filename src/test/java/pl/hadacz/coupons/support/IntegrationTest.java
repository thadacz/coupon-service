package pl.hadacz.coupons.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/// Full application context on a real PostgreSQL. All integration tests share one context,
/// so Spring caches it and the container starts once per test run.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
public @interface IntegrationTest {
}
