package com.qbits.stories.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.stories.DedupeProperties;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.domain.GroupCandidate;
import com.qbits.stories.domain.Section;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnthropicStoryGrouperTest {

  @RegisterExtension
  static WireMockExtension api =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().gzipDisabled(true))
          .build();

  private static final DedupeProperties PROPS =
      new DedupeProperties(true, "v2", Duration.ofSeconds(5), 1000);

  private static final List<GroupCandidate> CANDIDATES =
      List.of(
          candidate(1, "OpenAI", "OpenAI explains its plan for EU text watermarking rules"),
          candidate(2, "The Verge", "OpenAI Rolls Out Invisible Text Watermarks in ChatGPT"),
          candidate(3, "TechCrunch", "OpenAI to watermark ChatGPT text for EU users"),
          candidate(4, "BBC News", "Chip maker opens plant"));

  private final JsonMapper json = JsonMapper.builder().build();
  private AnthropicClient client;
  private AnthropicStoryGrouper grouper;

  private static GroupCandidate candidate(int n, String source, String headline) {
    return new GroupCandidate(
        new UUID(0, n),
        headline,
        "Our summary " + n + ".\nSecond line.",
        Section.GLOBAL_AI_TECH,
        "src-" + n,
        source,
        n == 1,
        Instant.parse("2026-10-05T15:00:37Z").plusSeconds(3600L * n),
        false);
  }

  @BeforeEach
  void setUp() {
    client =
        AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(api.baseUrl())
            .timeout(Duration.ofSeconds(2))
            .maxRetries(0)
            .build();
    grouper = new AnthropicStoryGrouper(client, StoryFixtures.props(), PROPS);
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  @Test
  void sendsOnlyOurOwnTextAndForcesTheTool() { // 008 R1.3
    reply(200, toolResponse(Map.of("groups", List.of())));

    grouper.group(CANDIDATES);

    JsonNode body =
        json.readTree(api.getAllServeEvents().getFirst().getRequest().getBodyAsString());
    assertThat(body.get("model").asString()).isEqualTo("test-model");
    assertThat(body.get("max_tokens").asInt()).isEqualTo(1000);
    assertThat(body.get("tool_choice").get("name").asString()).isEqualTo("group_stories");
    assertThat(body.get("system").toString())
        .contains("SAME EVENT", "Only group stories when", "news and an analysis");
    String text = body.get("messages").get(0).get("content").asString();
    assertThat(text)
        .startsWith("[1] section=AI Wire source=OpenAI published=2026-10-05T16:00:00Z")
        .contains("headline: OpenAI explains its plan for EU text watermarking rules")
        .contains("summary: Our summary 1. Second line.")
        .contains("[4] section=AI Wire source=BBC News")
        .doesNotContain("00000000-0000"); // ids are never sent
  }

  @Test
  void mapsNumbersBackToStoryIds() { // 008 R1.1
    reply(200, toolResponse(Map.of("groups", List.of(List.of(1, 2, 3)))));

    List<Set<UUID>> groups = grouper.group(CANDIDATES);

    assertThat(groups).containsExactly(Set.of(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3)));
  }

  @Test
  void numbersOutsideTheListBecomeAnUnknownId() { // 008 R1.4: the rules drop such groups
    reply(200, toolResponse(Map.of("groups", List.of(List.of(4, 9)))));

    assertThat(grouper.group(CANDIDATES))
        .containsExactly(Set.of(new UUID(0, 4), AnthropicStoryGrouper.UNKNOWN));
  }

  @Test
  void fewerThanTwoStoriesNeedNoCall() {
    assertThat(grouper.group(CANDIDATES.subList(0, 1))).isEmpty();
    assertThat(api.getAllServeEvents()).isEmpty();
  }

  @Test
  void serverErrorsAreTemporary() { // 008 R3.1
    reply(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"x\"}}");

    assertThatThrownBy(() -> grouper.group(CANDIDATES))
        .isInstanceOf(StoryWriter.WriterUnavailable.class);
  }

  @Test
  void badRequestsAreRejected() {
    reply(
        400,
        "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"x\"}}");

    assertThatThrownBy(() -> grouper.group(CANDIDATES))
        .isInstanceOf(StoryWriter.WriterRejected.class);
  }

  @Test
  void aMissingToolCallIsRetriedLater() { // 008 design: treated as unavailable
    reply(
        200,
        json.writeValueAsString(
            Map.of(
                "id", "msg_1",
                "type", "message",
                "role", "assistant",
                "model", "test-model",
                "content", List.of(Map.of("type", "text", "text", "No repeats.")),
                "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 10, "output_tokens", 5))));

    assertThatThrownBy(() -> grouper.group(CANDIDATES))
        .isInstanceOf(StoryWriter.WriterUnavailable.class);
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
                Map.of("type", "tool_use", "id", "tu_1", "name", "group_stories", "input", input)),
            "stop_reason",
            "tool_use",
            "usage",
            Map.of("input_tokens", 100, "output_tokens", 20)));
  }
}
