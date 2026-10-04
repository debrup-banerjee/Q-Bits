package com.qbits.stories;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.MutableClock;
import com.qbits.common.Ids;
import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Region;
import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourceType;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.persistence.JobBackoffRepository;
import com.qbits.stories.persistence.StoryRepository;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class StoryJobIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  @Autowired WriterBudgetRepository budget;
  @Autowired JobBackoffRepository backoff;
  @Autowired LockingTaskExecutor locks;
  @Autowired TransactionTemplate tx;

  private MutableClock clock;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
  }

  @Test
  void processesNewestFirstAndPassesSourceContext() { // 002 R1.1, R1.2
    UUID older = item(NOW.minus(Duration.ofHours(5)));
    UUID newer = item(NOW.minus(Duration.ofHours(1)));
    FakeStoryWriter writer = new FakeStoryWriter(StoryFixtures::validDraft);

    job(writer, props(1, 500)).runOnce();

    assertThat(writer.inputs)
        .extracting(StoryInput::publishedAt)
        .containsExactly(NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(5)));
    StoryInput sent = writer.inputs.getFirst();
    assertThat(sent.sourceName()).isEqualTo("Example India");
    assertThat(sent.region()).isEqualTo("INDIA");
    assertThat(sent.sectionHint()).isEqualTo("india-ai");
    assertThat(stories.find(older)).isPresent();
    assertThat(stories.find(newer)).isPresent();
  }

  @Test
  void expiresPendingItemsOlderThan72Hours() { // 002 R9.3
    UUID old = item(NOW.minus(Duration.ofHours(72)).minusSeconds(1));
    FakeStoryWriter writer = new FakeStoryWriter(StoryFixtures::validDraft);

    job(writer, props(1, 500)).runOnce();

    assertThat(status(old)).isEqualTo(StoryStatus.EXPIRED);
    assertThat(writer.inputs).isEmpty();
  }

  @Test
  void stopsForTheDayAtTheCallCap() { // 002 R9.1
    for (int i = 0; i < 5; i++) {
      item(NOW.minus(Duration.ofMinutes(i + 1)));
    }
    FakeStoryWriter writer = new FakeStoryWriter(StoryFixtures::validDraft);

    job(writer, props(1, 3)).runOnce();

    assertThat(writer.inputs).hasSize(3);
    assertThat(budget.used(LocalDate.of(2026, 10, 3))).isEqualTo(3);
    assertThat(countByStatus(StoryStatus.PENDING)).isEqualTo(2);
    assertThat(backoff.find(StoryJob.NAME)).isEmpty(); // a full budget is not an outage

    job(writer, props(1, 3)).runOnce();
    assertThat(writer.inputs).hasSize(3);
  }

  @Test
  void neverRunsMoreThanTheParallelLimit() { // 002 R9.4
    for (int i = 0; i < 8; i++) {
      item(NOW.minus(Duration.ofMinutes(i + 1)));
    }
    AtomicInteger running = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    FakeStoryWriter writer =
        new FakeStoryWriter(
            in -> {
              peak.accumulateAndGet(running.incrementAndGet(), Math::max);
              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              running.decrementAndGet();
              return StoryFixtures.validDraft(in);
            });

    job(writer, props(3, 500)).runOnce();

    assertThat(peak.get()).isBetween(1, 3);
    assertThat(countByStatus(StoryStatus.PUBLISHED)).isEqualTo(8);
  }

  @Test
  void backsOffWhileTheServiceIsDownThenRecovers() { // 002 R9.2
    UUID id = item(NOW.minus(Duration.ofHours(1)));
    FakeStoryWriter writer = new FakeStoryWriter(StoryFixtures::validDraft).thenUnavailable();
    StoryJob job = job(writer, props(1, 500));

    job.runOnce();
    assertThat(status(id)).isEqualTo(StoryStatus.PENDING);
    assertThat(backoff.find(StoryJob.NAME)).isPresent();

    clock.advance(Duration.ofMinutes(1));
    job.runOnce();
    assertThat(writer.inputs).hasSize(1); // still backing off

    clock.advance(Duration.ofMinutes(2));
    job.runOnce();
    assertThat(status(id)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(backoff.find(StoryJob.NAME)).isEmpty();
  }

  @Test
  void publishedStoriesAreNotRewritten() { // 002 R1.3
    item(NOW.minus(Duration.ofHours(1)));
    FakeStoryWriter writer = new FakeStoryWriter(StoryFixtures::validDraft);
    StoryJob job = job(writer, props(1, 500));

    job.runOnce();
    job.runOnce();

    assertThat(writer.inputs).hasSize(1);
  }

  private StoryJob job(FakeStoryWriter writer, StoryWriterProperties props) {
    StoryService service =
        new StoryService(writer, stories, new ItemStore(items), props, tx, clock);
    return new StoryJob(
        new ItemStore(items), registry(), service, budget, backoff, props, locks, clock);
  }

  private static StoryWriterProperties props(int parallelism, int cap) {
    return new StoryWriterProperties(
        StoryWriterProperties.Mode.DIGEST,
        "anthropic",
        "test-model",
        "v2",
        800,
        cap,
        parallelism,
        20,
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(30));
  }

  private static SourceRegistry registry() {
    URI uri = URI.create("https://in.example/feed");
    return new SourceRegistry(
        List.of(
            new Source(
                "src",
                "Example India",
                uri,
                uri,
                SourceType.RSS,
                uri,
                LocalDate.of(2026, 10, 3),
                true,
                false,
                Region.INDIA,
                "india-ai",
                null)));
  }

  private UUID item(Instant published) {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            "src",
            "https://in.example/" + id,
            "AI lab in Chennai",
            "Teaser.",
            id.toString(),
            published,
            false,
            published,
            1.0,
            StoryStatus.PENDING,
            false));
    return id;
  }

  private StoryStatus status(UUID id) {
    return items.findById(id).orElseThrow().storyStatus();
  }

  private long countByStatus(StoryStatus status) {
    return jdbc.queryForObject(
        "select count(*) from items where story_status = ?", Long.class, status.name());
  }
}
