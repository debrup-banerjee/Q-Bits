package com.qbits;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/** Base class for HTTP-level tests against a running server on a random port. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "qbits.sources-file=src/test/resources/sources/one-enabled.yml")
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class ApiTest {

  @LocalServerPort protected int port;
  @Autowired protected JdbcTemplate jdbc;
  protected RestClient http;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresTestSupport.register(registry);
  }

  @BeforeEach
  void setUpApi() {
    http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    jdbc.execute(
        """
        do $$ declare r record; begin
          for r in select tablename from pg_tables where schemaname = 'public'
                   and tablename not in ('flyway_schema_history', 'shedlock') loop
            execute 'truncate table ' || quote_ident(r.tablename) || ' restart identity cascade';
          end loop;
        end $$;
        """);
  }
}
