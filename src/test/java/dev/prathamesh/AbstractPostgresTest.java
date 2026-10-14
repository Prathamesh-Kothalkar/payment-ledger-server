package dev.prathamesh;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One real Postgres shared by all integration tests (H2 would hide locking behaviour). */
public abstract class AbstractPostgresTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 10k tasks queue for connections. The long timeout keeps them waiting
        // instead of failing, so we measure the service rather than the pool default.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "300000");
    }
}