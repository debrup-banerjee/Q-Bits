package com.qbits.stories.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.ErrorObject;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchErroredResult;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchResult;
import com.qbits.stories.BatchStoryWriter;
import com.qbits.stories.StoryWriter.WriterRejected;
import com.qbits.stories.StoryWriter.WriterUnavailable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes stories through the Anthropic Message Batches API (spec 006 R2). */
public class AnthropicBatchStoryWriter implements BatchStoryWriter {

  private static final Logger log = LoggerFactory.getLogger(AnthropicBatchStoryWriter.class);
  static final String MALFORMED = "malformed result";

  private final AnthropicClient client;
  private final StoryRequests requests;

  public AnthropicBatchStoryWriter(AnthropicClient client, StoryRequests requests) {
    this.client = client;
    this.requests = requests;
  }

  @Override
  public String submit(List<BatchRequest> batch) {
    if (!requests.modelConfigured()) {
      throw new WriterUnavailable("no model configured; set QBITS_STORY_MODEL", null);
    }
    BatchCreateParams.Builder params = BatchCreateParams.builder();
    for (BatchRequest r : batch) {
      params.addRequest(
          BatchCreateParams.Request.builder()
              .customId(r.customId())
              .params(
                  BatchCreateParams.Request.Params.builder()
                      .model(requests.model())
                      .maxTokens(requests.maxTokens())
                      .system(requests.systemPrompt())
                      .addUserMessage(requests.userText(r.input(), r.retryFeedback()))
                      .addTool(requests.tool())
                      .toolChoice(requests.toolChoice())
                      .build())
              .build());
    }
    return call(() -> client.messages().batches().create(params.build()).id());
  }

  @Override
  public BatchState status(String batchId) {
    MessageBatch batch = call(() -> client.messages().batches().retrieve(batchId));
    return batch.processingStatus().equals(MessageBatch.ProcessingStatus.ENDED)
        ? BatchState.ENDED
        : BatchState.IN_PROGRESS;
  }

  /**
   * Reads the results file line by line. A line that cannot be read affects only its own item: it
   * becomes {@link BatchOutcome.Errored} when its custom id is readable, and is skipped otherwise,
   * so that item goes back for the next edition (spec 006 R2.4, R6.2).
   */
  @Override
  public List<BatchOutcome> results(String batchId) {
    return call(
        () -> {
          List<BatchOutcome> out = new ArrayList<>();
          try (HttpResponseFor<StreamResponse<MessageBatchIndividualResponse>> response =
                  client.messages().batches().withRawResponse().resultsStreaming(batchId);
              BufferedReader lines =
                  new BufferedReader(
                      new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
              if (!line.isBlank()) {
                lineOutcome(batchId, line).ifPresent(out::add);
              }
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          return out;
        });
  }

  private Optional<BatchOutcome> lineOutcome(String batchId, String line) {
    try {
      MessageBatchIndividualResponse r =
          ObjectMappers.jsonMapper().readValue(line, MessageBatchIndividualResponse.class);
      return Optional.of(outcome(r));
    } catch (Exception e) { // one unreadable line must not hold up the rest of the edition
      Optional<String> customId = customIdOf(line);
      log.warn(
          "batch result line unreadable batch={} customId={} error={}",
          batchId,
          customId.orElse("unknown"),
          e.getClass().getSimpleName());
      return customId.map(id -> new BatchOutcome.Errored(id, MALFORMED));
    }
  }

  private static Optional<String> customIdOf(String line) {
    try {
      var id = ObjectMappers.jsonMapper().readTree(line).get("custom_id");
      return id != null && id.isTextual() && !id.asText().isBlank()
          ? Optional.of(id.asText())
          : Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private BatchOutcome outcome(MessageBatchIndividualResponse r) {
    String customId = r.customId();
    MessageBatchResult result = r.result();
    if (result.succeeded().isPresent()) {
      try {
        return new BatchOutcome.Succeeded(
            customId, requests.draftFrom(result.succeeded().get().message()));
      } catch (WriterRejected e) {
        return new BatchOutcome.Errored(customId, e.getMessage());
      } catch (RuntimeException e) { // a tool input that is not an object, a missing field, ...
        return new BatchOutcome.Errored(customId, MALFORMED);
      }
    }
    if (result.errored().isPresent()) {
      return new BatchOutcome.Errored(customId, errorText(result.errored().get()));
    }
    return new BatchOutcome.NotRun(customId);
  }

  /** The error's type and message, e.g. {@code invalid_request_error: max_tokens too large}. */
  private static String errorText(MessageBatchErroredResult errored) {
    try {
      ErrorObject error = errored.error().error();
      return error.type().asString() + ": " + error.message();
    } catch (AnthropicInvalidDataException e) {
      return "unknown error";
    }
  }

  private static <T> T call(Supplier<T> action) {
    try {
      return action.get();
    } catch (BadRequestException | UnprocessableEntityException e) {
      throw new WriterRejected("HTTP " + e.statusCode(), e);
    } catch (AnthropicServiceException e) {
      throw new WriterUnavailable("HTTP " + e.statusCode(), e);
    } catch (AnthropicIoException | AnthropicRetryableException e) {
      throw new WriterUnavailable("network: " + e.getClass().getSimpleName(), e);
    } catch (AnthropicException e) {
      // An answer the client could not read (for example a batch with no id): try again later.
      throw new WriterUnavailable("unreadable response: " + e.getClass().getSimpleName(), e);
    }
  }
}
