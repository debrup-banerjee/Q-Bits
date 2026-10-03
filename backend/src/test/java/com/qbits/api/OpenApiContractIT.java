package com.qbits.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.ApiTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenAPI document is the contract for web and mobile clients. This test fails when the API
 * changes without updating {@code packages/api-client/openapi.json}. To update it, run with {@code
 * -Dqbits.updateOpenApi=true} and regenerate the client.
 */
class OpenApiContractIT extends ApiTest {

  static final Path CONTRACT = Path.of("../packages/api-client/openapi.json");

  private final JsonMapper json =
      JsonMapper.builder()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  @Test
  void committedContractMatchesTheApi() throws Exception {
    JsonNode live = json.readTree(http.get().uri("/v3/api-docs").retrieve().body(String.class));
    ((tools.jackson.databind.node.ObjectNode) live).remove("servers"); // port differs per run
    String rendered = json.writeValueAsString(json.treeToValue(live, Object.class)) + "\n";

    if (Boolean.getBoolean("qbits.updateOpenApi") || !Files.exists(CONTRACT)) {
      Files.writeString(CONTRACT, rendered);
    }
    assertThat(json.readTree(Files.readString(CONTRACT))).isEqualTo(json.readTree(rendered));
  }
}
