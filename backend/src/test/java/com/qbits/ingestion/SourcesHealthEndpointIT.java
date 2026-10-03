package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.PostgresTestSupport;
import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import com.qbits.ingestion.persistence.SourceStateRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "qbits.sources-file=src/test/resources/sources/one-enabled.yml")
@ActiveProfiles("test")
class SourcesHealthEndpointIT {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    PostgresTestSupport.register(registry);
  }

  @LocalServerPort int port;
  @Autowired SourceStateRepository states;

  @Test
  void healthShowsPerSourceDetail() { // 001 R7.5
    Instant success = Instant.parse("2026-10-03T05:00:00Z");
    states.save(new SourceState("example-lab", null, null, success, success, 0, SourceHealth.OK));

    String body =
        RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/actuator/health")
            .retrieve()
            .body(String.class);

    assertThat(body)
        .contains("\"sources\"")
        .contains("\"example-lab\"")
        .contains("\"lastSuccessAt\":\"2026-10-03T05:00:00Z\"")
        .contains("\"consecutiveFailures\":0");
  }
}
