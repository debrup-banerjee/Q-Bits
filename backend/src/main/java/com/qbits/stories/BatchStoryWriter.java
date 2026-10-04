package com.qbits.stories;

import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import java.util.List;
import java.util.Optional;

/**
 * Writes many stories in one asynchronous batch at half price (spec 006 R2). Requests are the same
 * as single calls. Throws {@link StoryWriter.WriterUnavailable} or {@link
 * StoryWriter.WriterRejected} on failures, like the single writer.
 */
public interface BatchStoryWriter {

  /**
   * One story to write. {@code customId} is the item id. {@code feedback} lists the rules the
   * previous draft broke (retry round, spec 006 R2.5); it is empty on the first round.
   */
  record BatchRequest(String customId, StoryInput input, String feedback) {

    public BatchRequest {
      feedback = feedback == null ? "" : feedback.strip();
    }

    /** A first-round request, with no feedback. */
    public static BatchRequest first(String customId, StoryInput input) {
      return new BatchRequest(customId, input, "");
    }

    /** True for a retry-round request. */
    public boolean isRetry() {
      return !feedback.isEmpty();
    }

    /** The feedback when this is a retry, in the form the request builder takes. */
    public Optional<String> retryFeedback() {
      return isRetry() ? Optional.of(feedback) : Optional.empty();
    }
  }

  /** Whether the batch has finished. */
  enum BatchState {
    IN_PROGRESS,
    ENDED
  }

  /** The answer for one request. */
  sealed interface BatchOutcome {
    String customId();

    record Succeeded(String customId, StoryDraft draft) implements BatchOutcome {}

    record Errored(String customId, String reason) implements BatchOutcome {}

    /** Expired or cancelled before it ran: try again in the next edition (spec 006 R6.2). */
    record NotRun(String customId) implements BatchOutcome {}
  }

  /** Submits the batch and returns its id. */
  String submit(List<BatchRequest> requests);

  BatchState status(String batchId);

  List<BatchOutcome> results(String batchId);
}
