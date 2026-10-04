package com.qbits.stories.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.qbits.stories.BatchStoryWriter.BatchOutcome;
import com.qbits.stories.BatchStoryWriter.BatchRequest;
import com.qbits.stories.BatchStoryWriter.BatchState;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.domain.StoryInput;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnthropicBatchStoryWriterTest {

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
          "GLOBAL",
          null,
          Instant.parse("2026-10-03T04:00:00Z"));

  private final JsonMapper json = JsonMapper.builder().build();
  private AnthropicClient client;
  private AnthropicBatchStoryWriter writer;

  @BeforeEach
  void setUp() {
    client =
        AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl(api.baseUrl())
            .timeout(Duration.ofSeconds(2))
            .maxRetries(0)
            .build();
    writer = new AnthropicBatchStoryWriter(client, new StoryRequests(StoryFixtures.props(), json));
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  private String batch(String status) {
    return json.writeValueAsString(
        Map.ofEntries(
            Map.entry("id", "msgbatch_1"),
            Map.entry("type", "message_batch"),
            Map.entry("processing_status", status),
            Map.entry(
                "request_counts",
                Map.of("processing", 0, "succeeded", 2, "errored", 1, "canceled", 0, "expired", 1)),
            Map.entry("created_at", "2026-10-04T00:30:00Z"),
            Map.entry("expires_at", "2026-10-05T00:30:00Z"),
            Map.entry("ended_at", "2026-10-04T01:00:00Z"),
            Map.entry("results_url", api.baseUrl() + "/v1/messages/batches/msgbatch_1/results")));
  }

  @Test
  void submitsOneRequestPerItemWithTheSameParamsAsSingleCalls() { // 006 R2.1, R2.6
    api.stubFor(
        post(urlPathEqualTo("/v1/messages/batches"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(batch("in_progress"))));

    String id =
        writer.submit(
            List.of(
                BatchRequest.first("item-1", INPUT),
                new BatchRequest("item-2", INPUT, "SUMMARY_LEN: too short")));

    assertThat(id).isEqualTo("msgbatch_1");
    JsonNode body =
        json.readTree(api.getAllServeEvents().getFirst().getRequest().getBodyAsString());
    JsonNode requests = body.get("requests");
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0).get("custom_id").asString()).isEqualTo("item-1");
    JsonNode params = requests.get(0).get("params");
    assertThat(params.get("model").asString()).isEqualTo("test-model");
    assertThat(params.get("tool_choice").get("name").asString()).isEqualTo("write_story");
    JsonNode sent = json.readTree(params.get("messages").get(0).get("content").asString());
    assertThat(sent.propertyNames())
        .containsExactly("title", "excerpt", "sourceName", "region", "sectionHint", "publishedAt");
    assertThat(requests.get(1).get("params").get("messages").get(0).get("content").asString())
        .contains("Your previous answer broke these rules: SUMMARY_LEN: too short");
  }

  @Test
  void reportsStatus() { // 006 R2.3
    api.stubFor(
        get(urlPathEqualTo("/v1/messages/batches/msgbatch_1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(batch("in_progress"))));
    assertThat(writer.status("msgbatch_1")).isEqualTo(BatchState.IN_PROGRESS);

    api.stubFor(
        get(urlPathEqualTo("/v1/messages/batches/msgbatch_1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(batch("ended"))));
    assertThat(writer.status("msgbatch_1")).isEqualTo(BatchState.ENDED);
  }

  @Test
  void readsSucceededErroredAndExpiredResults() { // 006 R2.4, R6.2
    api.stubFor(
        get(urlPathEqualTo("/v1/messages/batches/msgbatch_1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(batch("ended"))));
    Map<String, Object> message =
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
                Map.of(
                    "type",
                    "tool_use",
                    "id",
                    "tu_1",
                    "name",
                    "write_story",
                    "input",
                    Map.of("isAi", false, "reason", "Not AI"))),
            "stop_reason",
            "tool_use",
            "usage",
            Map.of("input_tokens", 100, "output_tokens", 20));
    String jsonl =
        String.join(
            "\n",
            json.writeValueAsString(
                Map.of(
                    "custom_id", "a", "result", Map.of("type", "succeeded", "message", message))),
            json.writeValueAsString(
                Map.of(
                    "custom_id",
                    "b",
                    "result",
                    Map.of(
                        "type",
                        "errored",
                        "error",
                        Map.of(
                            "type",
                            "error",
                            "error",
                            Map.of("type", "invalid_request_error", "message", "bad"))))),
            json.writeValueAsString(Map.of("custom_id", "c", "result", Map.of("type", "expired"))));
    api.stubFor(
        get(urlPathEqualTo("/v1/messages/batches/msgbatch_1/results"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/x-jsonl").withBody(jsonl)));

    List<BatchOutcome> results = writer.results("msgbatch_1");

    assertThat(results).hasSize(3);
    assertThat(results.get(0))
        .isInstanceOfSatisfying(
            BatchOutcome.Succeeded.class, s -> assertThat(s.draft().isAi()).isFalse());
    // 006 R2.4: the error type and message are kept
    assertThat(results.get(1))
        .isEqualTo(new BatchOutcome.Errored("b", "invalid_request_error: bad"));
    assertThat(results.get(2)).isEqualTo(new BatchOutcome.NotRun("c"));
  }

  @Test
  void serverErrorsAreTemporary() { // 006 R6.1
    api.stubFor(
        post(urlPathEqualTo("/v1/messages/batches"))
            .willReturn(
                aResponse()
                    .withStatus(529)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"m\"}}")));

    assertThatThrownBy(() -> writer.submit(List.of(BatchRequest.first("a", INPUT))))
        .isInstanceOf(StoryWriter.WriterUnavailable.class);
  }

  @Test
  void anUnreadableSubmitAnswerIsTemporary() { // 006 R6.1
    api.stubFor(
        post(urlPathEqualTo("/v1/messages/batches"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"type\":\"message_batch\"}"))); // no batch id

    assertThatThrownBy(() -> writer.submit(List.of(BatchRequest.first("a", INPUT))))
        .isInstanceOf(StoryWriter.WriterUnavailable.class);
  }

  @Test
  void aMalformedResultLineAffectsOnlyItsOwnItem() { // 006 R2.4, R6.2
    api.stubFor(
        get(urlPathEqualTo("/v1/messages/batches/msgbatch_1"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(batch("ended"))));
    String jsonl =
        String.join(
            "\n",
            json.writeValueAsString(
                Map.of("custom_id", "a", "result", Map.of("type", "succeeded", "message", "x"))),
            succeeded("b", "not an object"),
            "{not json at all",
            succeeded("c", Map.of("isAi", false, "reason", "Not AI")),
            json.writeValueAsString(Map.of("custom_id", "d", "result", Map.of("type", "expired"))));
    api.stubFor(
        get(urlPathEqualTo("/v1/messages/batches/msgbatch_1/results"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/x-jsonl").withBody(jsonl)));

    List<BatchOutcome> results = writer.results("msgbatch_1");

    assertThat(results)
        .containsExactly(
            new BatchOutcome.Errored("a", AnthropicBatchStoryWriter.MALFORMED),
            new BatchOutcome.Errored("b", AnthropicBatchStoryWriter.MALFORMED),
            results.get(2), // "c" checked below; the unreadable line without an id is skipped
            new BatchOutcome.NotRun("d"));
    assertThat(results.get(2))
        .isInstanceOfSatisfying(
            BatchOutcome.Succeeded.class,
            s -> {
              assertThat(s.customId()).isEqualTo("c");
              assertThat(s.draft().isAi()).isFalse();
            });
  }

  private String succeeded(String customId, Object toolInput) {
    Map<String, Object> message =
        Map.of(
            "id",
            "msg_" + customId,
            "type",
            "message",
            "role",
            "assistant",
            "model",
            "test-model",
            "content",
            List.of(
                Map.of(
                    "type", "tool_use", "id", "tu_1", "name", "write_story", "input", toolInput)),
            "stop_reason",
            "tool_use",
            "usage",
            Map.of("input_tokens", 100, "output_tokens", 20));
    return json.writeValueAsString(
        Map.of("custom_id", customId, "result", Map.of("type", "succeeded", "message", message)));
  }
}
