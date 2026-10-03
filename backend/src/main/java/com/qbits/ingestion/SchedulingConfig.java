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
    private final RetentionJob retention;
    private final com.qbits.stories.StoryJob stories;
    private final com.qbits.resources.ResourceCheckJob links;

    Triggers(
        IngestionJob ingestion,
        RetentionJob retention,
        com.qbits.stories.StoryJob stories,
        com.qbits.resources.ResourceCheckJob links) {
      this.ingestion = ingestion;
      this.retention = retention;
      this.stories = stories;
      this.links = links;
    }

    @Scheduled(fixedDelayString = "${qbits.resources.tick}", initialDelayString = "PT90S")
    void checkLinks() {
      links.runOnce();
    }

    @Scheduled(fixedDelayString = "${qbits.story-writer.tick}", initialDelayString = "PT60S")
    void writeStories() {
      stories.runOnce();
    }

    @Scheduled(fixedDelayString = "${qbits.ingestion.tick}", initialDelayString = "PT30S")
    void ingest() {
      ingestion.runOnce();
    }

    @Scheduled(cron = "${qbits.ingestion.retention-cron}", zone = "UTC")
    void deleteOldItems() {
      retention.runOnce();
    }
  }
}
