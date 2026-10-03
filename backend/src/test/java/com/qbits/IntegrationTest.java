package com.qbits;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for integration tests: full context, real PostgreSQL, clean tables per test. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

  @Autowired protected JdbcTemplate jdbc;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresTestSupport.register(registry);
  }

  @BeforeEach
  void cleanDatabase() {
    jdbc.execute(
        """
        do $$ declare r record; begin
          for r in select tablename from pg_tables
                   where schemaname = 'public' and tablename <> 'flyway_schema_history' loop
            execute 'truncate table ' || quote_ident(r.tablename) || ' restart identity cascade';
          end loop;
        end $$;
        """);
  }
}
