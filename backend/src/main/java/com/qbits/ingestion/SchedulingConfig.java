package com.qbits.ingestion;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Database locks shared by app instances, and the scheduled triggers. */
@Configuration
class SchedulingConfig {

  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  @Bean
  LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
    return new DefaultLockingTaskExecutor(lockProvider);
  }

  /** Scheduled triggers. Switched off in tests with qbits.scheduling.enabled=false. */
  @Configuration
  @EnableScheduling
  @ConditionalOnProperty(
      name = "qbits.scheduling.enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class Triggers {

    private final IngestionJob ingestion;

    Triggers(IngestionJob ingestion) {
      this.ingestion = ingestion;
    }

    @Scheduled(fixedDelayString = "${qbits.ingestion.tick}", initialDelayString = "PT30S")
    void ingest() {
      ingestion.runOnce();
    }
  }
}
