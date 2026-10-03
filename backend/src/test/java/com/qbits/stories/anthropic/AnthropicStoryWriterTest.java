package com.qbits.stories.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnthropicStoryWriterTest {

  @RegisterExtension
  static WireMockExtension api =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  private static final StoryInput INPUT =
      new StoryInput(
          "Lab launches model",
          "Short teaser.",
          "TechCrunch",
          "INDIA",
          "india-ai",
          Instant.parse("2026-10-03T04:00:00Z"));

  private final JsonMapper json = JsonMapper.builder().build();
  private AnthropicClient client;
  private AnthropicStoryWriter writer;

  @BeforeEach
  void setUp() {
    client =
        AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(api.baseUrl())
            .timeout(Duration.ofSeconds(2))
            .maxRetries(0)
            .build();
    writer = new AnthropicStoryWriter(client, StoryFixtures.props(), json);
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  @Test
  void sendsOnlyAllowedFieldsAndForcesTheTool() { // 002 R1.2
    reply(
        200,
        toolResponse(
            Map.of(
                "isAi",
                true,
                "reason",
                "AI",
                "section",
                "INDIA_AI",
                "headline",
                "H",
                "summary",
                "S",
                "keyTerms",
                List.of(Map.of("term", "GPU", "meaning", "A chip.")))));

    writer.write(INPUT, Optional.empty());

    JsonNode body =
        json.readTree(api.getAllServeEvents().getFirst().getRequest().getBodyAsString());
    assertThat(body.get("model").asString()).isEqualTo("test-model");
    assertThat(body.get("tool_choice").get("type").asString()).isEqualTo("tool");
    assertThat(body.get("tool_choice").get("name").asString()).isEqualTo("write_story");
    assertThat(body.get("system").toString()).contains("Use only facts in");
    String userText = body.get("messages").get(0).get("content").asString();
    JsonNode sent = json.readTree(userText);
    assertThat(sent.propertyNames())
        .containsExactly("title", "excerpt", "sourceName", "region", "sectionHint", "publishedAt");
  }

  @Test
  void mapsToolOutputToDraft() { // 002 R10.1
    reply(
        200,
        toolResponse(
            Map.of(
                "isAi",
                true,
                "reason",
                "AI",
                "section",
                "INDIA_AI",
                "headline",
                "H",
                "summary",
                "S",
                "keyTerms",
                List.of(Map.of("term", "GPU", "meaning", "A chip.")))));

    StoryDraft draft = writer.write(INPUT, Optional.empty());

    assertThat(draft.isAi()).isTrue();
    assertThat(draft.section()).isEqualTo(Section.INDIA_AI);
    assertThat(draft.keyTerms())
        .singleElement()
        .satisfies(t -> assertThat(t.term()).isEqualTo("GPU"));
    assertThat(draft.model()).isEqualTo("test-model");
    assertThat(draft.inputTokens()).isEqualTo(100);
    assertThat(draft.outputTokens()).isEqualTo(200);
  }

  @Test
  void includesFeedbackOnRetry() { // 002 R8.2
    reply(200, toolResponse(Map.of("isAi", false, "reason", "Not AI")));

    writer.write(INPUT, Optional.of("SUMMARY_LEN: too short"));

    assertThat(api.getAllServeEvents().getFirst().getRequest().getBodyAsString())
        .contains("Your previous answer broke these rules: SUMMARY_LEN: too short");
  }

  @Test
  void unknownSectionBecomesNullForTheValidator() {
    reply(
        200,
        toolResponse(
            Map.of(
                "isAi",
                true,
                "reason",
                "AI",
                "section",
                "SPORTS",
                "headline",
                "H",
                "summary",
                "S")));

    assertThat(writer.write(INPUT, Optional.empty()).section()).isNull();
  }

  @Test
  void rateLimitAndServerErrorsAreTemporary() { // 002 R9.2
    for (int status : new int[] {429, 500, 529, 401}) {
      api.resetAll();
      reply(status, "{\"type\":\"error\",\"error\":{\"type\":\"x\",\"message\":\"m\"}}");
      assertThatThrownBy(() -> writer.write(INPUT, Optional.empty()))
          .as("status %s", status)
          .isInstanceOf(StoryWriter.WriterUnavailable.class);
    }
  }

  @Test
  void timeoutIsTemporary() { // 002 R9.2
    api.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(5_000)));

    assertThatThrownBy(() -> writer.write(INPUT, Optional.empty()))
        .isInstanceOf(StoryWriter.WriterUnavailable.class);
  }

  @Test
  void badRequestIsRejected() {
    reply(
        400,
        "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"m\"}}");

    assertThatThrownBy(() -> writer.write(INPUT, Optional.empty()))
        .isInstanceOf(StoryWriter.WriterRejected.class);
  }

  @Test
  void missingModelIsTemporaryAndMakesNoCall() {
    var noModel =
        new com.qbits.stories.StoryWriterProperties(
            "anthropic",
            "",
            "v1",
            800,
            500,
            4,
            20,
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(30));
    AnthropicStoryWriter w = new AnthropicStoryWriter(client, noModel, json);

    assertThatThrownBy(() -> w.write(INPUT, Optional.empty()))
        .isInstanceOf(StoryWriter.WriterUnavailable.class);
    api.verify(0, postRequestedFor(urlPathEqualTo("/v1/messages")));
  }

  private void reply(int status, String body) {
    api.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  private String toolResponse(Map<String, Object> input) {
    return json.writeValueAsString(
        Map.of(
            "id",
            "msg_1",
            "type",
            "message",
            "role",
            "assistant",
            "model",
            "test-model",
            "content",
            List.of(
                Map.of("type", "tool_use", "id", "tu_1", "name", "write_story", "input", input)),
            "stop_reason",
            "tool_use",
            "usage",
            Map.of("input_tokens", 100, "output_tokens", 200)));
  }
}
