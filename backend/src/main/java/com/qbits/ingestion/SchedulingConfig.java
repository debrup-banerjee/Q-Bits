package com.qbits.ingestion;

import com.qbits.resources.ResourceCheckJob;
import com.qbits.stories.StoryJob;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.digest.EditionJob;
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
    private final StoryJob stories;
    private final ResourceCheckJob links;
    private final EditionJob editions;
    private final boolean digestMode;

    Triggers(
        IngestionJob ingestion,
        RetentionJob retention,
        StoryJob stories,
        ResourceCheckJob links,
        EditionJob editions,
        StoryWriterProperties writer) {
      this.ingestion = ingestion;
      this.retention = retention;
      this.stories = stories;
      this.links = links;
      this.editions = editions;
      this.digestMode = !writer.realtime();
    }

    /** Daily digest (spec 006). Runs only in digest mode. */
    @Scheduled(fixedDelayString = "${qbits.digest.tick}", initialDelayString = "PT45S")
    void runDigest() {
      if (digestMode) {
        editions.runOnce();
      }
    }

    @Scheduled(fixedDelayString = "${qbits.resources.tick}", initialDelayString = "PT90S")
    void checkLinks() {
      links.runOnce();
    }

    @Scheduled(fixedDelayString = "${qbits.story-writer.tick}", initialDelayString = "PT60S")
    void writeStories() {
      if (!digestMode) {
        stories.runOnce(); // one-at-a-time writing, only in realtime mode (spec 006 R6.4)
      }
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
