package com.habit.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that need real PostgreSQL (LAG window function,
 * native SQL, date arithmetic). A single shared container is reused across
 * the JVM for speed — Hibernate's `ddl-auto=create-drop` handles per-suite
 * schema isolation via the `-PintegrationTest` profile properties.
 */
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("habit_tracker_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("app.scheduler.enabled", () -> "false");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }
}
