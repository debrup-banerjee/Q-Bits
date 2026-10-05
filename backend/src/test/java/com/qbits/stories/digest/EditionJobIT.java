package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.MutableClock;
import com.qbits.common.Ids;
import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.stories.BatchStoryWriter.BatchOutcome;
import com.qbits.stories.BatchStoryWriter.BatchRequest;
import com.qbits.stories.FakeBatchStoryWriter;
import com.qbits.stories.FakeStoryWriter;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryInputs;
import com.qbits.stories.StoryService;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.StoryWriterProperties;
import com.qbits.stories.persistence.StoryRepository;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class EditionJobIT extends IntegrationTest {

  /** 06:00 IST on 4 October 2026. */
  private static final Instant CUTOFF = Instant.parse("2026-10-04T00:30:00Z");

  @Autowired ItemRepository items;
  @Autowired StoryRepository storyRepo;
  @Autowired EditionRepository editions;
  @Autowired WriterBudgetRepository budget;
  @Autowired EditionPublisher publisher;
  @Autowired LockingTaskExecutor locks;
  @Autowired TransactionTemplate tx;

  private MutableClock clock;
  private FakeBatchStoryWriter batches;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(CUTOFF.plusSeconds(60));
    batches = new FakeBatchStoryWriter(StoryFixtures::validDraft);
  }

  private EditionJob job(int cap) {
    StoryWriterProperties writerProps =
        new StoryWriterProperties(
            StoryWriterProperties.Mode.DIGEST,
            "anthropic",
            "test-model",
            "v1",
            800,
            cap,
            4,
            20,
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(30));
    StoryService service =
        new StoryService(
            new FakeStoryWriter(StoryFixtures::validDraft),
            storyRepo,
            itemStore(),
            writerProps,
            tx,
            clock);
    return new EditionJob(
        editions,
        itemStore(),
        batches,
        service,
        new StoryInputs(new SourceRegistry(List.of())),
        budget,
        new EditionPublisher(itemStore(), editions, tx),
        writerProps,
        props(),
        locks,
        clock);
  }

  private ItemStore itemStore() {
    return new ItemStore(items);
  }

  private static DigestProperties props() {
    return new DigestProperties(
        LocalTime.of(6, 0),
        ZoneId.of("Asia/Kolkata"),
        Duration.ofMinutes(5),
        Duration.ofMinutes(10),
        Duration.ofMinutes(30),
        Duration.ofHours(6),
        Duration.ofHours(2));
  }

  @Test
  void cutsAtSixAmIstWithItemsFetchedBeforeTheCutoffNewestFirst() { // 006 R1.1–R1.3, R2.1
    UUID older = item(CUTOFF.minus(Duration.ofHours(20)), CUTOFF.minus(Duration.ofHours(19)));
    UUID newer = item(CUTOFF.minus(Duration.ofHours(2)), CUTOFF.minus(Duration.ofHours(1)));
    UUID leftover = item(CUTOFF.minus(Duration.ofHours(40)), CUTOFF.minus(Duration.ofHours(39)));
    UUID late =
        item(CUTOFF.minus(Duration.ofHours(1)), CUTOFF.plusSeconds(30)); // fetched after cut-off
    batches.autoFinish = false;

    job(500).runOnce();

    Edition e = editions.findOpen().orElseThrow();
    assertThat(e.cutoffAt()).isEqualTo(CUTOFF);
    assertThat(e.status()).isEqualTo(EditionStatus.SUBMITTED);
    assertThat(batches.submitted.getFirst())
        .extracting(BatchRequest::customId)
        .containsExactlyInAnyOrder(older.toString(), newer.toString(), leftover.toString());
    assertThat(batches.submitted.getFirst())
        .extracting(BatchRequest::customId)
        .doesNotContain(late.toString());
  }

  @Test
  void publishesTheWholeEditionAtOnceWhenTheBatchEnds() { // 006 R2.3, R3.1, R3.2
    UUID a = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    UUID b = item(CUTOFF.minus(Duration.ofHours(4)), CUTOFF.minus(Duration.ofHours(3)));
    batches.autoFinish = false;
    EditionJob job = job(500);

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // still running
    assertThat(status(a)).isEqualTo(StoryStatus.PENDING);
    assertThat(status(b)).isEqualTo(StoryStatus.PENDING);

    batches.finish("batch_1");
    clock.advance(Duration.ofMinutes(5));
    job.runOnce(); // poll not yet due (10 min)
    assertThat(status(a)).isEqualTo(StoryStatus.PENDING);

    clock.advance(Duration.ofMinutes(5));
    job.runOnce();

    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(b)).isEqualTo(StoryStatus.PUBLISHED);
    Edition e = editions.findLatestPublished().orElseThrow();
    assertThat(e.published()).isEqualTo(2);
    assertThat(e.publishedAt()).isEqualTo(clock.instant());
  }

  @Test
  void runsOneRetryRoundWithFeedback() { // 006 R2.5
    UUID fixed = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.answer =
        r ->
            new BatchOutcome.Succeeded(
                r.customId(),
                r.isRetry()
                    ? StoryFixtures.validDraft(r.input())
                    : StoryFixtures.invalidDraft(r.input()));
    EditionJob job = job(500);

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // main ended → retry submitted
    assertThat(editions.findOpen().orElseThrow().status()).isEqualTo(EditionStatus.RETRYING);
    assertThat(batches.submitted.get(1))
        .singleElement()
        .satisfies(r -> assertThat(r.feedback()).contains("SUMMARY_LEN"));

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(status(fixed)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(items.findRetryFeedback(fixed)).isEmpty(); // kept only while the item waits
  }

  @Test
  void stillInvalidAfterRetryIsRejected() { // 006 R2.5
    UUID bad = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.answer =
        r -> new BatchOutcome.Succeeded(r.customId(), StoryFixtures.invalidDraft(r.input()));
    EditionJob job = job(500);

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(status(bad)).isEqualTo(StoryStatus.REJECTED);
    assertThat(items.findRetryFeedback(bad)).isEmpty();
    assertThat(editions.findLatestPublished().orElseThrow().rejected()).isEqualTo(1);
  }

  @Test
  void countsComeFromItemStatusesSoARepeatedStepIsStillCounted() { // 006 R1.4
    UUID notAi = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    UUID bad = item(CUTOFF.minus(Duration.ofHours(4)), CUTOFF.minus(Duration.ofHours(3)));
    UUID good = item(CUTOFF.minus(Duration.ofHours(5)), CUTOFF.minus(Duration.ofHours(4)));
    batches.answer =
        r ->
            new BatchOutcome.Succeeded(
                r.customId(),
                r.customId().equals(bad.toString())
                    ? StoryFixtures.invalidDraft(r.input())
                    : StoryFixtures.validDraft(r.input()));
    batches.autoFinish = false;
    EditionJob job = job(500);
    job.runOnce();
    // An earlier run handled this item and stopped before it could record anything else.
    itemStore().setStoryStatus(notAi, StoryStatus.NOT_AI, "Sport");
    batches.finish("batch_1");

    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // main round: "good" written, "bad" goes to the retry round
    batches.finish("batch_2");
    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // retry round: "bad" rejected, edition published

    Edition e = editions.findLatestPublished().orElseThrow();
    assertThat(status(good)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(e.published()).isEqualTo(1);
    assertThat(e.notAi()).isEqualTo(1);
    assertThat(e.rejected()).isEqualTo(1);
  }

  @Test
  void anEditionWithNoCallsLeftKeepsCollectingInsteadOfPublishingEmpty() { // 006 R2.2, R3.3
    UUID a = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    budget.takeUpTo(today, 1, Integer.MAX_VALUE); // an operator rewrite used today's only call
    EditionJob job = job(1);

    job.runOnce();
    clock.advance(Duration.ofMinutes(5));
    job.runOnce();

    assertThat(editions.findOpen().orElseThrow().status()).isEqualTo(EditionStatus.COLLECTING);
    assertThat(editions.findLatestPublished()).isEmpty();
    assertThat(batches.submitted).isEmpty();
    assertThat(jdbc.queryForObject("select edition_id from items where id = ?", UUID.class, a))
        .isNull();

    jdbc.update("delete from writer_budget"); // calls free again
    clock.advance(Duration.ofMinutes(5));
    job.runOnce(); // the next tick submits

    assertThat(batches.submitted).singleElement().satisfies(b -> assertThat(b).hasSize(1));
  }

  @Test
  void anEditionWithNoCallsLeftIsPublishedEmptyAtTheDeadline() { // 006 R2.2, R3.3
    UUID a = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    budget.takeUpTo(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), 1, Integer.MAX_VALUE);
    EditionJob job = job(1);
    job.runOnce();

    clock.advance(Duration.ofHours(6));
    job.runOnce();

    Edition e = editions.findLatestPublished().orElseThrow();
    assertThat(e.published()).isZero();
    assertThat(status(a)).isEqualTo(StoryStatus.PENDING);
    assertThat(batches.submitted).isEmpty();
  }

  @Test
  void notAiAndErroredResultsAreRecorded() { // 006 R2.4
    UUID notAi = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    UUID errored = item(CUTOFF.minus(Duration.ofHours(4)), CUTOFF.minus(Duration.ofHours(3)));
    batches.answer =
        r ->
            r.customId().equals(notAi.toString())
                ? new BatchOutcome.Succeeded(r.customId(), StoryFixtures.notAi(r.input()))
                : new BatchOutcome.Errored(r.customId(), "overloaded_error: Overloaded");
    EditionJob job = job(500);

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(status(notAi)).isEqualTo(StoryStatus.NOT_AI);
    assertThat(status(errored)).isEqualTo(StoryStatus.REJECTED);
    // 006 R2.4: rejected "with the error" — its type and message are kept, prefixed once
    assertThat(items.findStoryNote(errored))
        .contains("PROVIDER_ERROR: overloaded_error: Overloaded");
    Edition e = editions.findLatestPublished().orElseThrow();
    assertThat(e.notAi()).isEqualTo(1);
    assertThat(e.rejected()).isEqualTo(1);
  }

  @Test
  void respectsTheDailyCapAndLeavesTheRestForTomorrow() { // 006 R2.2
    item(CUTOFF.minus(Duration.ofHours(1)), CUTOFF.minus(Duration.ofMinutes(50)));
    item(CUTOFF.minus(Duration.ofHours(2)), CUTOFF.minus(Duration.ofMinutes(110)));
    UUID oldest = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofMinutes(170)));

    job(2).runOnce();

    assertThat(batches.submitted.getFirst()).hasSize(2);
    assertThat(batches.submitted.getFirst())
        .extracting(BatchRequest::customId)
        .doesNotContain(oldest.toString());
  }

  @Test
  void expiredRequestsGoBackForTheNextEdition() { // 006 R6.2
    UUID expired = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.answer = r -> new BatchOutcome.NotRun(r.customId());
    EditionJob job = job(500);

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(status(expired)).isEqualTo(StoryStatus.PENDING);
    assertThat(
            jdbc.queryForObject("select edition_id from items where id = ?", UUID.class, expired))
        .isNull();
  }

  @Test
  void publishesWhatIsReadyAfterSixHours() { // 006 R3.3
    UUID waiting = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.autoFinish = false;
    EditionJob job = job(500);

    job.runOnce();
    clock.advance(Duration.ofHours(6).plusMinutes(1));
    job.runOnce();

    Edition e = editions.findLatestPublished().orElseThrow();
    assertThat(e.published()).isZero();
    assertThat(status(waiting)).isEqualTo(StoryStatus.PENDING);
    assertThat(
            jdbc.queryForObject("select edition_id from items where id = ?", UUID.class, waiting))
        .isNull();
  }

  @Test
  void retriesSubmittingEveryThirtyMinutes() { // 006 R6.1
    item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.failNextSubmit = new StoryWriter.WriterUnavailable("down", null);
    EditionJob job = job(500);

    job.runOnce();
    assertThat(editions.findOpen().orElseThrow().status()).isEqualTo(EditionStatus.COLLECTING);
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();
    assertThat(batches.submitted).isEmpty();

    clock.advance(Duration.ofMinutes(20));
    job.runOnce();
    assertThat(batches.submitted).hasSize(1);
  }

  @Test
  void onlyOneEditionPerDay() { // 006 R1.1
    item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    EditionJob job = job(500);
    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // published
    item(CUTOFF.plus(Duration.ofHours(1)), CUTOFF.plus(Duration.ofHours(1)));

    clock.advance(Duration.ofHours(5));
    job.runOnce();
    assertThat(batches.submitted).hasSize(1);

    clock.advance(Duration.ofHours(19)); // next day 06:00 IST passed
    job.runOnce();
    assertThat(batches.submitted).hasSize(2);
  }

  @Test
  void failedResultsDownloadIsReadAgainAtTheNextPoll() { // 006 R2.4, R3.1
    UUID a = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    EditionJob job = job(500);
    job.runOnce();
    batches.failNextResults = new StoryWriter.WriterUnavailable("connection reset", null);

    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // batch ended, but the results could not be read

    Edition e = editions.findOpen().orElseThrow();
    assertThat(e.status()).isEqualTo(EditionStatus.SUBMITTED);
    assertThat(editions.findLatestPublished()).isEmpty();
    assertThat(status(a)).isEqualTo(StoryStatus.PENDING);
    assertThat(jdbc.queryForObject("select edition_id from items where id = ?", UUID.class, a))
        .isEqualTo(e.id());

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(editions.findLatestPublished().orElseThrow().published()).isEqualTo(1);
    assertThat(batches.submitted).hasSize(1); // nothing written or paid for twice
  }

  @Test
  void failedRetryResultsDownloadIsReadAgainAtTheNextPoll() { // 006 R2.4, R2.5
    UUID fixed = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.answer =
        r ->
            new BatchOutcome.Succeeded(
                r.customId(),
                r.isRetry()
                    ? StoryFixtures.validDraft(r.input())
                    : StoryFixtures.invalidDraft(r.input()));
    EditionJob job = job(500);
    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // retry round submitted
    batches.failNextResults = new StoryWriter.WriterUnavailable("connection reset", null);

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();
    assertThat(editions.findOpen().orElseThrow().status()).isEqualTo(EditionStatus.RETRYING);
    assertThat(status(fixed)).isEqualTo(StoryStatus.PENDING);

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();
    assertThat(status(fixed)).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void aDayFirstCutAfterItsDeadlineIsCutNowAndStillWritten() { // 006 R1.1, R1.2, R3.3, R5.5
    Instant yesterday = CUTOFF.minus(Duration.ofDays(1));
    UUID previous = Ids.newId();
    editions.create(
        new Edition(
            previous,
            yesterday,
            EditionStatus.COLLECTING,
            null,
            null,
            0,
            0,
            0,
            0,
            yesterday,
            null,
            yesterday,
            null));
    editions.markPublished(previous, 0, 0, 0, yesterday.plus(Duration.ofHours(1)));
    UUID before = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    UUID after = item(CUTOFF.plus(Duration.ofHours(3)), CUTOFF.plus(Duration.ofHours(3)));
    clock = new MutableClock(CUTOFF.plus(Duration.ofHours(7))); // first run after the deadline
    EditionQueries queries = new EditionQueries(editions, props(), StoryFixtures.props(), clock);
    EditionJob job = job(500);

    job.runOnce();

    Edition e = editions.findOpen().orElseThrow();
    assertThat(e.cutoffAt()).isEqualTo(clock.instant());
    assertThat(e.status()).isEqualTo(EditionStatus.SUBMITTED);
    assertThat(batches.submitted.getFirst())
        .extracting(BatchRequest::customId)
        .containsExactlyInAnyOrder(before.toString(), after.toString());
    // Until it is published, readers see yesterday's edition with the late notice.
    assertThat(queries.isLate(queries.latestPublished().orElseThrow())).isTrue();

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    Edition published = editions.findLatestPublished().orElseThrow();
    assertThat(published.id()).isEqualTo(e.id());
    assertThat(published.published()).isEqualTo(2);
    assertThat(queries.isLate(published)).isFalse();

    // Still one edition for the day: later ticks today cut nothing more.
    item(clock.instant().plusSeconds(60), clock.instant().plusSeconds(60));
    clock.advance(Duration.ofHours(5));
    job.runOnce();
    assertThat(batches.submitted).hasSize(1);

    // The next day is cut at its scheduled time again.
    moveTo(CUTOFF.plus(Duration.ofDays(1)).plusSeconds(60));
    job.runOnce();
    assertThat(batches.submitted).hasSize(2);
    assertThat(editions.findOpen().orElseThrow().cutoffAt())
        .isEqualTo(CUTOFF.plus(Duration.ofDays(1)));
  }

  @Test
  void retryBatchStaysWithinTheDailyCap() { // 006 R2.2, R2.5
    UUID newer = item(CUTOFF.minus(Duration.ofHours(1)), CUTOFF.minus(Duration.ofMinutes(50)));
    UUID older = item(CUTOFF.minus(Duration.ofHours(2)), CUTOFF.minus(Duration.ofMinutes(110)));
    batches.answer =
        r -> new BatchOutcome.Succeeded(r.customId(), StoryFixtures.invalidDraft(r.input()));
    EditionJob job = job(3); // main batch uses 2, leaving room for one retry

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(batches.submitted).hasSize(2);
    assertThat(batches.submitted.get(1))
        .extracting(BatchRequest::customId)
        .containsExactly(newer.toString());
    assertThat(budget.used(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))).isEqualTo(3);
    // The item over the cap waits for the next edition, without feedback.
    assertThat(status(older)).isEqualTo(StoryStatus.PENDING);
    assertThat(jdbc.queryForObject("select edition_id from items where id = ?", UUID.class, older))
        .isNull();
    assertThat(items.findRetryFeedback(older)).isEmpty();
  }

  @Test
  void noRetryBatchWhenTheCapIsUsedUp() { // 006 R2.2
    UUID a = item(CUTOFF.minus(Duration.ofHours(1)), CUTOFF.minus(Duration.ofMinutes(50)));
    batches.answer =
        r -> new BatchOutcome.Succeeded(r.customId(), StoryFixtures.invalidDraft(r.input()));
    EditionJob job = job(1);

    job.runOnce();
    clock.advance(Duration.ofMinutes(10));
    job.runOnce();

    assertThat(batches.submitted).hasSize(1);
    assertThat(budget.used(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))).isEqualTo(1);
    assertThat(editions.findLatestPublished()).isPresent();
    assertThat(status(a)).isEqualTo(StoryStatus.PENDING);
    assertThat(jdbc.queryForObject("select edition_id from items where id = ?", UUID.class, a))
        .isNull();
  }

  @Test
  void retriesSubmittingTheRetryBatchEveryThirtyMinutes() { // 006 R6.1, R2.5
    UUID fixed = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.answer =
        r ->
            new BatchOutcome.Succeeded(
                r.customId(),
                r.isRetry()
                    ? StoryFixtures.validDraft(r.input())
                    : StoryFixtures.invalidDraft(r.input()));
    EditionJob job = job(500);
    job.runOnce();
    batches.failNextSubmit = new StoryWriter.WriterUnavailable("rate limited", null);

    clock.advance(Duration.ofMinutes(10));
    job.runOnce(); // main ended; retry submit fails

    Edition e = editions.findOpen().orElseThrow();
    assertThat(e.status()).isEqualTo(EditionStatus.RETRYING);
    assertThat(e.retryBatchId()).isNull();
    assertThat(editions.findLatestPublished()).isEmpty();
    assertThat(status(fixed)).isEqualTo(StoryStatus.PENDING);

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();
    assertThat(batches.submitted).hasSize(1); // not yet: 30 minutes

    clock.advance(Duration.ofMinutes(20));
    job.runOnce();
    assertThat(batches.submitted).hasSize(2);
    assertThat(batches.submitted.get(1).getFirst().isRetry()).isTrue();

    clock.advance(Duration.ofMinutes(10));
    job.runOnce();
    assertThat(status(fixed)).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void aBatchThatEndedJustBeforeTheDeadlineIsStillRead() { // 006 R3.3
    UUID a = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    batches.autoFinish = false;
    EditionJob job = job(500);
    job.runOnce();
    moveTo(CUTOFF.plus(Duration.ofHours(5)).plus(Duration.ofMinutes(55)));
    job.runOnce(); // polled; still running
    batches.finish("batch_1");

    clock.advance(Duration.ofMinutes(6)); // past the deadline, before the next poll was due
    job.runOnce();

    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(editions.findLatestPublished().orElseThrow().published()).isEqualTo(1);
  }

  @Test
  void twoCutsAtOnceNeverMoveAnItemTwice() throws Exception { // 006 R1.2, R6.3
    UUID a = item(CUTOFF.minus(Duration.ofHours(3)), CUTOFF.minus(Duration.ofHours(2)));
    UUID b = item(CUTOFF.minus(Duration.ofHours(4)), CUTOFF.minus(Duration.ofHours(3)));
    UUID first = Ids.newId();
    UUID second = Ids.newId();
    for (UUID id : List.of(first, second)) {
      Instant cut = id.equals(first) ? CUTOFF : CUTOFF.plusSeconds(1);
      editions.create(
          new Edition(
              id, cut, EditionStatus.COLLECTING, null, null, 0, 0, 0, 0, cut, null, cut, null));
    }
    Instant since = CUTOFF.minus(EditionSchedule.MAX_WINDOW);
    CountDownLatch assigned = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> one =
          pool.submit(
              () ->
                  tx.execute(
                      s -> {
                        int n = items.assignToEdition(first, CUTOFF, since, 10);
                        assigned.countDown();
                        await(commit);
                        return n;
                      }));
      assigned.await(10, TimeUnit.SECONDS);
      Future<Integer> two =
          pool.submit(() -> tx.execute(s -> items.assignToEdition(second, CUTOFF, since, 10)));
      Thread.sleep(300);
      commit.countDown();

      assertThat(one.get(10, TimeUnit.SECONDS)).isEqualTo(2);
      assertThat(two.get(10, TimeUnit.SECONDS)).isZero();
    } finally {
      pool.shutdownNow();
    }
    assertThat(items.findPendingInEdition(first))
        .extracting(Item::id)
        .containsExactlyInAnyOrder(a, b);
    assertThat(items.findPendingInEdition(second)).isEmpty();
  }

  private void moveTo(Instant at) {
    clock.advance(Duration.between(clock.instant(), at));
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private UUID item(Instant published, Instant fetched) {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            "src",
            "https://a.example/" + id,
            "Lab shares new AI tool",
            "Short teaser.",
            id.toString(),
            published,
            false,
            fetched,
            1.0,
            StoryStatus.PENDING,
            false));
    return id;
  }

  private StoryStatus status(UUID id) {
    return items.findById(id).orElseThrow().storyStatus();
  }
}
