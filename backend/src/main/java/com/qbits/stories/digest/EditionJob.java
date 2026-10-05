package com.qbits.stories.digest;

import com.qbits.common.Ids;
import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.stories.BatchStoryWriter;
import com.qbits.stories.BatchStoryWriter.BatchOutcome;
import com.qbits.stories.BatchStoryWriter.BatchRequest;
import com.qbits.stories.BatchStoryWriter.BatchState;
import com.qbits.stories.StoryInputs;
import com.qbits.stories.StoryService;
import com.qbits.stories.StoryService.Applied;
import com.qbits.stories.StoryService.DraftResult;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.domain.StoryNote;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskWithResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs the daily digest (spec 006): at the cut-off, puts waiting items into an edition and submits
 * one batch; polls it; validates results; runs one retry round with feedback; then publishes the
 * whole edition at once. Each tick does whatever step is due, so restarts are safe.
 */
@Service
public class EditionJob {

  private static final Logger log = LoggerFactory.getLogger(EditionJob.class);
  private static final String LOCK = "digest";

  private final EditionRepository editions;
  private final ItemStore items;
  private final BatchStoryWriter batches;
  private final StoryService stories;
  private final StoryInputs inputs;
  private final WriterBudgetRepository budget;
  private final EditionPublisher publisher;
  private final StoryWriterProperties writerProps;
  private final DigestProperties props;
  private final LockingTaskExecutor locks;
  private final Clock clock;
  private final EditionSchedule schedule;

  public EditionJob(
      EditionRepository editions,
      ItemStore items,
      BatchStoryWriter batches,
      StoryService stories,
      StoryInputs inputs,
      WriterBudgetRepository budget,
      EditionPublisher publisher,
      StoryWriterProperties writerProps,
      DigestProperties props,
      LockingTaskExecutor locks,
      Clock clock) {
    this.editions = editions;
    this.items = items;
    this.batches = batches;
    this.stories = stories;
    this.inputs = inputs;
    this.budget = budget;
    this.publisher = publisher;
    this.writerProps = writerProps;
    this.props = props;
    this.locks = locks;
    this.clock = clock;
    this.schedule = new EditionSchedule(props.cutoffTime(), props.zone());
  }

  public void runOnce() {
    locks.executeWithLock((Runnable) this::tick, lock());
  }

  /**
   * Cuts an edition now, whatever the time (operator command, spec 006 R6.3). Takes the same lock
   * as the scheduled tick, so the two never pick items at the same moment.
   */
  public UUID cutNow() {
    TaskResult<UUID> result;
    try {
      result = locks.executeWithLock((TaskWithResult<UUID>) this::cutNowLocked, lock());
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException("edition cut failed", t);
    }
    if (!result.wasExecuted()) {
      throw new IllegalStateException("the edition job is running; try again in a few minutes");
    }
    return result.getResult();
  }

  private UUID cutNowLocked() {
    Instant now = clock.instant();
    if (editions.findOpen().isPresent()) {
      throw new IllegalStateException("an edition is already in progress");
    }
    Edition e = create(now);
    submitMain(e, now);
    return e.id();
  }

  /** ShedLock checks lock times against the real clock, so the lock uses it, not {@code clock}. */
  private static LockConfiguration lock() {
    return new LockConfiguration(Instant.now(), LOCK, Duration.ofMinutes(30), Duration.ZERO);
  }

  void tick() {
    Instant now = clock.instant();
    items.expirePendingBefore(now.minus(EditionSchedule.MAX_WINDOW)); // 002 R9.3

    Optional<Edition> open = editions.findOpen();
    if (open.isEmpty()) {
      Instant cutoff = schedule.latestCutoffAtOrBefore(now);
      if (editions.existsWithCutoffAtOrAfter(cutoff)) {
        return; // today's edition already cut (on schedule, late, or by the operator)
      }
      // A day first cut after its deadline (a deploy or an outage) is cut now instead, so its
      // waiting items are written rather than published as an empty edition (R1.2, R3.3).
      Instant cutAt = now.isAfter(cutoff.plus(props.publishDeadline())) ? now : cutoff;
      if (!cutAt.equals(cutoff)) {
        log.warn("edition cut late scheduledCutoff={} cutAt={}", cutoff, cutAt);
      }
      open = Optional.of(create(cutAt));
    }
    Edition e = open.get();

    if (now.isAfter(e.cutoffAt().plus(props.publishDeadline()))) {
      readEndedBatch(e, now); // a batch that ended since the last poll still counts
      log.warn("edition past its deadline; publishing what is ready edition={}", e.id());
      publisher.publish(e.id(), now); // R3.3
      return;
    }
    switch (e.status()) {
      case COLLECTING -> {
        if (submitDue(e, now)) {
          submitMain(e, now);
        }
      }
      case SUBMITTED -> {
        if (pollDue(e, now) && ended(e, e.batchId(), now)) {
          process(e, e.batchId(), 1)
              .ifPresent(
                  retry -> {
                    if (retry.isEmpty()) {
                      publisher.publish(e.id(), now);
                    } else {
                      submitRetry(e, retry, now);
                    }
                  });
        }
      }
      case RETRYING -> {
        if (e.retryBatchId() == null) {
          if (submitDue(e, now)) {
            submitRetry(e, retryItems(e), now); // R6.1: an earlier retry submit failed
          }
        } else if (pollDue(e, now) && ended(e, e.retryBatchId(), now)) {
          if (process(e, e.retryBatchId(), 2).isPresent()) {
            publisher.publish(e.id(), now);
          }
        }
      }
      default -> {}
    }
  }

  /** Past the deadline: reads the current batch's results if it has ended (spec 006 R3.3). */
  private void readEndedBatch(Edition e, Instant now) {
    String batchId =
        switch (e.status()) {
          case SUBMITTED -> e.batchId();
          case RETRYING -> e.retryBatchId();
          default -> null;
        };
    if (batchId != null && ended(e, batchId, now)) {
      process(e, batchId, e.status() == EditionStatus.RETRYING ? 2 : 1);
    }
  }

  private List<Item> retryItems(Edition e) {
    return items.pendingInEdition(e.id()).stream()
        .filter(i -> items.retryFeedback(i.id()).isPresent())
        .toList();
  }

  private Edition create(Instant cutoff) {
    Instant now = clock.instant();
    Edition e =
        new Edition(
            Ids.newId(),
            cutoff,
            EditionStatus.COLLECTING,
            null,
            null,
            0,
            0,
            0,
            0,
            now,
            null,
            now,
            null);
    editions.create(e);
    log.info("edition created edition={} cutoff={}", e.id(), cutoff);
    return e;
  }

  private void submitMain(Edition e, Instant now) {
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    int room = Math.max(0, writerProps.dailyCallCap() - budget.used(today));
    if (items.pendingInEdition(e.id()).isEmpty()) {
      if (room == 0) {
        // Today's calls are used up: keep collecting and try again at the next tick, rather than
        // publishing an empty edition; the deadline still applies (R2.2, R3.3).
        log.info("edition waits for the daily cap edition={}", e.id());
        return;
      }
      items.assignToEdition(
          e.id(), e.cutoffAt(), e.cutoffAt().minus(EditionSchedule.MAX_WINDOW), room);
    }
    List<Item> chosen = items.pendingInEdition(e.id());
    if (chosen.isEmpty()) {
      publisher.publish(e.id(), now); // nothing to write: an empty edition
      return;
    }
    List<BatchRequest> requests =
        chosen.stream().map(i -> BatchRequest.first(i.id().toString(), inputs.of(i))).toList();
    try {
      String batchId = batches.submit(requests);
      budget.takeUpTo(today, requests.size(), Integer.MAX_VALUE);
      editions.markSubmitted(e.id(), batchId, requests.size(), now);
      log.info("edition submitted edition={} batch={} items={}", e.id(), batchId, requests.size());
    } catch (StoryWriter.WriterUnavailable ex) {
      editions.markSubmitFailed(e.id(), now.plus(props.submitRetryEvery())); // R6.1
      log.warn("edition submit failed; will retry edition={} reason={}", e.id(), ex.getMessage());
    } catch (StoryWriter.WriterRejected ex) {
      items.releaseUnfinished(e.id());
      editions.updateStatus(e.id(), EditionStatus.FAILED);
      log.error("edition batch rejected edition={} reason={}", e.id(), ex.getMessage());
    }
  }

  /**
   * Submits the retry round for the items that failed validation, within today's remaining call cap
   * (R2.2, R2.5). Items over the cap go back for the next edition, newest kept first.
   */
  private void submitRetry(Edition e, List<Item> retry, Instant now) {
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    int room = Math.max(0, writerProps.dailyCallCap() - budget.used(today));
    List<Item> newestFirst =
        retry.stream()
            .sorted(Comparator.comparing(Item::publishedAt).thenComparing(Item::id).reversed())
            .toList();
    List<Item> sending = newestFirst.subList(0, Math.min(room, newestFirst.size()));
    List<Item> overCap = newestFirst.subList(sending.size(), newestFirst.size());
    overCap.forEach(i -> items.releaseFromEdition(i.id()));
    if (!overCap.isEmpty()) {
      log.info("edition retry over the daily cap edition={} released={}", e.id(), overCap.size());
    }
    if (sending.isEmpty()) {
      publisher.publish(e.id(), now);
      return;
    }
    List<BatchRequest> requests =
        sending.stream()
            .map(
                i ->
                    new BatchRequest(
                        i.id().toString(), inputs.of(i), items.retryFeedback(i.id()).orElse("")))
            .toList();
    try {
      String batchId = batches.submit(requests);
      budget.takeUpTo(today, requests.size(), Integer.MAX_VALUE);
      editions.markRetrying(e.id(), batchId, now);
      log.info(
          "edition retry submitted edition={} batch={} items={}", e.id(), batchId, requests.size());
    } catch (StoryWriter.WriterUnavailable ex) {
      editions.markRetrySubmitFailed(e.id(), now.plus(props.submitRetryEvery())); // R6.1
      log.warn(
          "edition retry submit failed; will retry edition={} reason={}", e.id(), ex.getMessage());
    } catch (StoryWriter.WriterRejected ex) {
      // The retry batch was refused outright: publish what is valid, the rest waits for tomorrow.
      log.warn("edition retry batch rejected edition={} reason={}", e.id(), ex.getMessage());
      publisher.publish(e.id(), now);
    }
  }

  private static boolean submitDue(Edition e, Instant now) {
    return e.nextSubmitAt() == null || !now.isBefore(e.nextSubmitAt());
  }

  private boolean pollDue(Edition e, Instant now) {
    return e.lastPolledAt() == null || !now.isBefore(e.lastPolledAt().plus(props.pollEvery()));
  }

  private boolean ended(Edition e, String batchId, Instant now) {
    editions.markPolled(e.id(), now);
    try {
      return batches.status(batchId) == BatchState.ENDED;
    } catch (StoryWriter.WriterUnavailable | StoryWriter.WriterRejected ex) {
      log.warn("batch status check failed edition={} reason={}", e.id(), ex.getMessage());
      return false;
    }
  }

  /**
   * Applies results and returns the items that need a retry round (attempt 1 only). Returns empty
   * when the results could not be read: nothing has changed, so the next poll reads them again.
   */
  private Optional<List<Item>> process(Edition e, String batchId, int attempt) {
    List<BatchOutcome> results;
    try {
      results = batches.results(batchId);
    } catch (StoryWriter.WriterUnavailable | StoryWriter.WriterRejected ex) {
      log.warn(
          "batch results unavailable; will read again at the next poll edition={} reason={}",
          e.id(),
          ex.getMessage());
      return Optional.empty();
    }
    Map<String, BatchOutcome> byId =
        results.stream()
            .collect(Collectors.toMap(BatchOutcome::customId, Function.identity(), (a, b) -> a));
    List<Item> retry = new ArrayList<>();
    for (Item item : items.pendingInEdition(e.id())) {
      if (attempt == 2 && items.retryFeedback(item.id()).isEmpty()) {
        continue; // not part of the retry round
      }
      BatchOutcome outcome = byId.get(item.id().toString());
      switch (outcome) {
        case BatchOutcome.Succeeded s -> {
          Applied applied =
              stories.applyDraft(item.id(), inputs.of(item), s.draft(), attempt, true);
          if (applied.result() == DraftResult.RETRY) {
            items.setRetryFeedback(item.id(), applied.feedback());
            retry.add(item);
          }
        }
        case BatchOutcome.Errored err ->
            items.setStoryStatus(
                item.id(),
                StoryStatus.REJECTED,
                StoryNote.of("PROVIDER_ERROR: " + err.reason())); // R2.4
        case BatchOutcome.NotRun n -> items.releaseFromEdition(item.id()); // R6.2
        case null -> items.releaseFromEdition(item.id());
      }
    }
    return Optional.of(List.copyOf(retry));
  }
}
