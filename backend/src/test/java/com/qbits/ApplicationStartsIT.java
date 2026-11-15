package com.qbits;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationStartsIT extends IntegrationTest {

  @Test
  void contextLoadsAndMigrationsRun() {
    Integer applied =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class);
    assertThat(applied).isPositive();
  }
}
