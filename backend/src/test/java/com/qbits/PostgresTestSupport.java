package com.qbits;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Supplies a PostgreSQL 16 database for integration tests.
 *
 * <p>Uses Testcontainers by default. If {@code QBITS_TEST_DB_URL} is set (for environments without
 * Docker), that database is used instead, with {@code QBITS_TEST_DB_USER} and {@code
 * QBITS_TEST_DB_PASSWORD}.
 */
public final class PostgresTestSupport {

  private static PostgreSQLContainer container;

  private PostgresTestSupport() {}

  public static synchronized void register(DynamicPropertyRegistry registry) {
    String url = System.getenv("QBITS_TEST_DB_URL");
    if (url != null && !url.isBlank()) {
      registry.add("spring.datasource.url", () -> url);
      registry.add("spring.datasource.username", () -> env("QBITS_TEST_DB_USER", "qbits"));
      registry.add("spring.datasource.password", () -> env("QBITS_TEST_DB_PASSWORD", "qbits"));
      return;
    }
    if (container == null) {
      container = new PostgreSQLContainer("postgres:16-alpine");
      container.start();
    }
    registry.add("spring.datasource.url", container::getJdbcUrl);
    registry.add("spring.datasource.username", container::getUsername);
    registry.add("spring.datasource.password", container::getPassword);
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
