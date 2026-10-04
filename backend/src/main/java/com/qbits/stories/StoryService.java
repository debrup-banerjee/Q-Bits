package com.qbits.stories;

import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.stories.domain.Story;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.domain.StoryNote;
import com.qbits.stories.domain.StoryValidator;
import com.qbits.stories.domain.ValidationFailure;
import com.qbits.stories.persistence.StoryRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns one pending item into a published story, or records why not: write → validate → retry once
 * with feedback → save (spec 002 R2, R8).
 */
@Service
public class StoryService {

  private static final Logger log = LoggerFactory.getLogger(StoryService.class);

  /** What happened to one item. */
  public enum Outcome {
    PUBLISHED,
    NOT_AI,
    REJECTED,
    UNAVAILABLE
  }

  /** Called before every request to the summary service; may refuse (daily cap). */
  public interface CallGate {
    boolean tryAcquire();

    CallGate UNLIMITED = () -> true;
  }

  private final StoryWriter writer;
  private final StoryRepository stories;
  private final ItemStore items;
  private final StoryWriterProperties props;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final StoryValidator validator = new StoryValidator();

  public StoryService(
      StoryWriter writer,
      StoryRepository stories,
      ItemStore items,
      StoryWriterProperties props,
      TransactionTemplate tx,
      Clock clock) {
    this.writer = writer;
    this.stories = stories;
    this.items = items;
    this.props = props;
    this.tx = tx;
    this.clock = clock;
  }

  public Outcome process(UUID itemId, StoryInput input, CallGate gate) {
    Optional<String> feedback = Optional.empty();
    for (int attempt = 1; attempt <= 2; attempt++) {
      if (!gate.tryAcquire()) {
        return Outcome.UNAVAILABLE; // daily cap reached; item stays PENDING (R9.1)
      }
      StoryDraft draft;
      try {
        draft = writer.write(input, feedback);
      } catch (StoryWriter.WriterUnavailable e) {
        log.warn("story writer unavailable item={} reason={}", itemId, e.getMessage());
        return Outcome.UNAVAILABLE; // stays PENDING (R9.2)
      } catch (StoryWriter.WriterRejected e) {
        items.setStoryStatus(
            itemId, StoryStatus.REJECTED, StoryNote.of("PROVIDER_ERROR: " + e.getMessage()));
        return Outcome.REJECTED;
      }

      Applied applied = applyDraft(itemId, input, draft, attempt, false);
      switch (applied.result()) {
        case SAVED:
          return Outcome.PUBLISHED;
        case NOT_AI:
          return Outcome.NOT_AI;
        case REJECTED:
          return Outcome.REJECTED;
        case RETRY:
          feedback = Optional.of(applied.feedback()); // R8.2
          break;
      }
    }
    throw new IllegalStateException("unreachable");
  }

  /** What happened to one draft. */
  public enum DraftResult {
    SAVED,
    NOT_AI,
    RETRY,
    REJECTED
  }

  /** Result of applying a draft; {@code feedback} is set for RETRY. */
  public record Applied(DraftResult result, String feedback) {}

  /**
   * Validates a draft and records the outcome. Shared by realtime writing and the daily batch (spec
   * 006 R2.4). With {@code holdBack} a valid story is saved as WRITTEN, to be published with its
   * edition; otherwise it is PUBLISHED at once. On the first attempt an invalid draft returns RETRY
   * with feedback; on the second it is REJECTED.
   */
  public Applied applyDraft(
      UUID itemId, StoryInput input, StoryDraft draft, int attempt, boolean holdBack) {
    if (!draft.isAi()) {
      items.setStoryStatus(itemId, StoryStatus.NOT_AI, StoryNote.of(draft.reason())); // 002 R2.2
      log.info("story not ai item={}", itemId);
      return new Applied(DraftResult.NOT_AI, null);
    }
    List<ValidationFailure> failures = validator.check(draft, input);
    if (failures.isEmpty()) {
      save(itemId, draft, attempt, holdBack ? StoryStatus.WRITTEN : StoryStatus.PUBLISHED);
      return new Applied(DraftResult.SAVED, null);
    }
    log.info("story draft invalid item={} attempt={} rules={}", itemId, attempt, ruleIds(failures));
    if (attempt >= 2) {
      items.setStoryStatus(
          itemId, StoryStatus.REJECTED, StoryNote.of(ruleIds(failures))); // 002 R8.3
      return new Applied(DraftResult.REJECTED, null);
    }
    String rules =
        failures.stream().map(ValidationFailure::toString).collect(Collectors.joining("; "));
    return new Applied(DraftResult.RETRY, rules);
  }

  private void save(UUID itemId, StoryDraft d, int attempts, StoryStatus status) {
    Story story =
        new Story(
            itemId,
            d.section(),
            d.headline().trim(),
            d.summary().trim(),
            d.keyTerms(),
            d.model(),
            props.promptVersion(),
            clock.instant(),
            d.inputTokens(),
            d.outputTokens(),
            attempts);
    tx.executeWithoutResult(
        txStatus -> {
          stories.save(story);
          items.setStoryStatus(itemId, status, null); // 002 R8.4, 006 R3.2
        });
    log.info(
        "story saved item={} status={} section={} attempts={}",
        itemId,
        status,
        d.section(),
        attempts);
  }

  private static String ruleIds(List<ValidationFailure> failures) {
    return failures.stream()
        .map(ValidationFailure::rule)
        .distinct()
        .collect(Collectors.joining(","));
  }
}
