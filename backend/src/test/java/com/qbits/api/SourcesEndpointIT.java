package com.qbits.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The public source list shows enabled sources only (003 R3.1). */
@TestPropertySource(
    properties = "qbits.sources-file=src/test/resources/sources/one-enabled-one-disabled.yml")
class SourcesEndpointIT extends ApiTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void listsOnlyEnabledSources() { // 003 R3.1
    JsonNode sources =
        json.readTree(http.get().uri("/api/v1/sources").retrieve().body(String.class));

    assertThat(sources).hasSize(1);
    assertThat(sources.get(0).get("name").asString()).isEqualTo("Example Lab");
    assertThat(sources.get(0).get("homepage").asString()).isEqualTo("https://lab.example.org");
    assertThat(sources.toString()).doesNotContain("Paused News", "paused.example.org");
  }
}
