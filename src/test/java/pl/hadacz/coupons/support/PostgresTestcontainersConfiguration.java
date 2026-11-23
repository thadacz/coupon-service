package pl.hadacz.coupons.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/// One PostgreSQL 19 container shared by every integration test context.
/// `@ServiceConnection` wires the datasource to it, so tests run against the same engine as production.
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:19");
    }
}
