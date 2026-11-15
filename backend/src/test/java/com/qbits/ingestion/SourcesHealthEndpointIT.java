package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.PostgresTestSupport;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import com.qbits.ingestion.persistence.SourceStateRepository;
import com.qbits.stories.digest.Edition;
import com.qbits.stories.digest.EditionRepository;
import com.qbits.stories.digest.EditionStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "qbits.sources-file=src/test/resources/sources/one-enabled.yml")
@ActiveProfiles("test")
@Import(com.qbits.TestBeans.class)
class SourcesHealthEndpointIT {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresTestSupport.register(registry);
  }

  @LocalServerPort int port;
  @Autowired SourceStateRepository states;
  @Autowired EditionRepository editions;
  @Autowired JdbcTemplate jdbc;

  @Test
  void healthShowsPerSourceDetail() { // 001 R7.5
    Instant success = Instant.parse("2026-10-03T05:00:00Z");
    states.save(
        new SourceState("example-lab", null, null, success, success, 0, SourceHealth.OK, null));

    String body = health();

    assertThat(body)
        .contains("\"sources\"")
        .contains("\"example-lab\"")
        .contains("\"lastSuccessAt\":\"2026-10-03T05:00:00Z\"")
        .contains("\"consecutiveFailures\":0");
  }

  @Test
  void healthShowsTheLatestEditionStatusCutoffAndPublishedTime() { // 006 R6.5
    jdbc.execute("truncate table editions cascade");
    Instant yesterday = Instant.parse("2026-10-03T00:30:00Z");
    Instant today = Instant.parse("2026-10-04T00:30:00Z");
    UUID published = Ids.newId();
    editions.create(edition(published, yesterday));
    editions.markSubmitted(published, "batch_1", 3, yesterday.plusSeconds(60));
    editions.markPublished(published, 3, 0, 0, Instant.parse("2026-10-03T01:12:00Z"));

    JsonNode digest = digest();
    assertThat(digest.path("status").asString()).isEqualTo("UP");
    assertThat(digest.at("/details/status").asString()).isEqualTo("PUBLISHED");
    assertThat(digest.at("/details/cutoffAt").asString()).isEqualTo("2026-10-03T00:30:00Z");
    assertThat(digest.at("/details/publishedAt").asString()).isEqualTo("2026-10-03T01:12:00Z");
    assertThat(digest.at("/details/stories").asInt()).isEqualTo(3);

    // Today's edition is in progress: its status shows, with the last published one beside it.
    UUID open = Ids.newId();
    editions.create(edition(open, today));
    editions.markSubmitted(open, "batch_2", 5, today.plusSeconds(60));

    digest = digest();
    assertThat(digest.at("/details/status").asString()).isEqualTo("SUBMITTED");
    assertThat(digest.at("/details/cutoffAt").asString()).isEqualTo("2026-10-04T00:30:00Z");
    assertThat(digest.at("/details/publishedAt").isMissingNode()).isTrue();
    assertThat(digest.at("/details/lastPublishedCutoffAt").asString())
        .isEqualTo("2026-10-03T00:30:00Z");
    assertThat(digest.at("/details/lastPublishedAt").asString()).isEqualTo("2026-10-03T01:12:00Z");

    // A failed edition is reported as such.
    editions.updateStatus(open, EditionStatus.FAILED);
    digest = digest();
    assertThat(digest.at("/details/status").asString()).isEqualTo("FAILED");
    // Readers still get the last edition, so health stays UP with a warning.
    assertThat(digest.path("status").asString()).isEqualTo("UP");
    assertThat(digest.at("/details/warning").asString()).contains("failed");
  }

  private static Edition edition(UUID id, Instant cutoff) {
    return new Edition(
        id, cutoff, EditionStatus.COLLECTING, null, null, 0, 0, 0, 0, cutoff, null, cutoff, null);
  }

  private JsonNode digest() {
    return JsonMapper.builder().build().readTree(health()).at("/components/digest");
  }

  private String health() {
    return RestClient.create()
        .get()
        .uri("http://localhost:" + port + "/actuator/health")
        .retrieve()
        .body(String.class);
  }
}
