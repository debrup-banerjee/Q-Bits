package com.qbits.stories;

import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.domain.Story;
import com.qbits.stories.domain.StoryDraft;
import com.qbits.stories.domain.StoryInput;
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
  private final ItemRepository items;
  private final StoryWriterProperties props;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final StoryValidator validator = new StoryValidator();

  public StoryService(
      StoryWriter writer,
      StoryRepository stories,
      ItemRepository items,
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
        items.updateStoryStatus(itemId, StoryStatus.REJECTED, "PROVIDER_ERROR: " + e.getMessage());
        return Outcome.REJECTED;
      }

      if (!draft.isAi()) {
        items.updateStoryStatus(itemId, StoryStatus.NOT_AI, draft.reason()); // R2
        log.info("story not ai item={}", itemId);
        return Outcome.NOT_AI;
      }
      List<ValidationFailure> failures = validator.check(draft, input);
      if (failures.isEmpty()) {
        publish(itemId, draft, attempt);
        return Outcome.PUBLISHED;
      }
      String rules =
          failures.stream().map(ValidationFailure::toString).collect(Collectors.joining("; "));
      log.info(
          "story draft invalid item={} attempt={} rules={}", itemId, attempt, ruleIds(failures));
      if (attempt == 2) {
        items.updateStoryStatus(itemId, StoryStatus.REJECTED, ruleIds(failures)); // R8.3
        return Outcome.REJECTED;
      }
      feedback = Optional.of(rules); // R8.2
    }
    throw new IllegalStateException("unreachable");
  }

  private void publish(UUID itemId, StoryDraft d, int attempts) {
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
        status -> {
          stories.save(story);
          items.updateStoryStatus(itemId, StoryStatus.PUBLISHED, null); // R8.4
        });
    log.info("story published item={} section={} attempts={}", itemId, d.section(), attempts);
  }

  private static String ruleIds(List<ValidationFailure> failures) {
    return failures.stream()
        .map(ValidationFailure::rule)
        .distinct()
        .collect(Collectors.joining(","));
  }
}
