package com.qbits.ingestion.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.ingestion.domain.FetchLogEntry;
import com.qbits.ingestion.domain.FetchStatus;
import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import com.qbits.ingestion.persistence.RobotsCacheRepository.CachedRobots;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StateAndLogRepositoryIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired SourceStateRepository states;
  @Autowired FetchLogRepository log;
  @Autowired RobotsCacheRepository robots;

  @Test
  void savesAndUpdatesSourceState() {
    states.save(SourceState.initial("src"));
    SourceState updated =
        new SourceState(
            "src", "\"etag-1\"", "Fri, 02 Oct 2026", NOW, NOW, 0, SourceHealth.OK, null);

    states.save(updated);

    assertThat(states.find("src")).contains(updated);
    assertThat(states.findAll()).hasSize(1);
  }

  @Test
  void storesAndClearsRetryAfter() { // 001 R7.1
    SourceState waiting = SourceState.initial("src").withRetryAfter(NOW.plusSeconds(7200));

    states.save(waiting);
    assertThat(states.find("src").orElseThrow().retryAfter()).isEqualTo(NOW.plusSeconds(7200));

    states.save(waiting.withRetryAfter(null));
    assertThat(states.find("src").orElseThrow().retryAfter()).isNull();
  }

  @Test
  void recordsFetchLogNewestFirst() { // 001 R7.2
    log.insert(entry(NOW, FetchStatus.OK));
    log.insert(entry(NOW.plusSeconds(60), FetchStatus.NOT_MODIFIED));

    assertThat(log.findBySource("src"))
        .extracting(FetchLogEntry::status)
        .containsExactly(FetchStatus.NOT_MODIFIED, FetchStatus.OK);
  }

  @Test
  void cachesRobotsPerHost() {
    robots.save(new CachedRobots("example.com", "User-agent: *", 200, NOW));
    robots.save(new CachedRobots("example.com", "Disallow: /", 200, NOW.plusSeconds(5)));

    assertThat(robots.find("example.com").orElseThrow().body()).isEqualTo("Disallow: /");
  }

  private static FetchLogEntry entry(Instant at, FetchStatus status) {
    return new FetchLogEntry("src", at, 120, status, 200, 3, 1, 2, 0, 0, null);
  }
}
