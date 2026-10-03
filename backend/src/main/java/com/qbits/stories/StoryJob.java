package com.qbits.stories;

import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import com.qbits.stories.StoryService.Outcome;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.persistence.JobBackoffRepository;
import com.qbits.stories.persistence.JobBackoffRepository.Backoff;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Background job that writes stories for pending items, newest first, within a daily call cap and a
 * parallelism limit. Backs off when the summary service is unavailable (spec 002 R1, R9).
 */
@Service
public class StoryJob {

  private static final Logger log = LoggerFactory.getLogger(StoryJob.class);
  static final String NAME = "stories";
  static final Duration WINDOW = Duration.ofHours(72);
  static final Duration FIRST_BACKOFF = Duration.ofMinutes(2);

  private final ItemRepository items;
  private final SourceRegistry registry;
  private final StoryService service;
  private final WriterBudgetRepository budget;
  private final JobBackoffRepository backoff;
  private final StoryWriterProperties props;
  private final LockingTaskExecutor locks;
  private final Clock clock;

  public StoryJob(
      ItemRepository items,
      SourceRegistry registry,
      StoryService service,
      WriterBudgetRepository budget,
      JobBackoffRepository backoff,
      StoryWriterProperties props,
      LockingTaskExecutor locks,
      Clock clock) {
    this.items = items;
    this.registry = registry;
    this.service = service;
    this.budget = budget;
    this.backoff = backoff;
    this.props = props;
    this.locks = locks;
    this.clock = clock;
  }

  public void runOnce() {
    LockConfiguration lock =
        new LockConfiguration(Instant.now(), NAME, Duration.ofMinutes(30), Duration.ZERO);
    locks.executeWithLock((Runnable) this::run, lock);
  }

  private void run() {
    Instant now = clock.instant();
    int expired = items.expirePendingBefore(now.minus(WINDOW)); // R9.3
    if (expired > 0) {
      log.info("stories expired count={}", expired);
    }
    Optional<Backoff> current = backoff.find(NAME);
    if (current.isPresent() && now.isBefore(current.get().nextAttemptAt())) {
      return; // R9.2: wait out the back-off
    }

    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    if (budget.used(today) >= props.dailyCallCap()) {
      return; // R9.1: cap reached; resume tomorrow
    }
    List<Item> batch = items.findPendingNewestFirst(now.minus(WINDOW), props.batchSize());
    if (batch.isEmpty()) {
      return;
    }

    AtomicBoolean unavailable = new AtomicBoolean(false);
    Semaphore slots = new Semaphore(props.parallelism()); // R9.4
    StoryService.CallGate gate = () -> budget.tryTake(today, props.dailyCallCap());
    List<Future<Outcome>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (Item item : batch) {
        try {
          slots.acquire(); // taken here so items start in newest-first order
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
        futures.add(
            pool.submit(
                () -> {
                  try {
                    if (unavailable.get()) {
                      return null; // stop the batch once the service is down
                    }
                    Outcome outcome = service.process(item.id(), input(item), gate);
                    if (outcome == Outcome.UNAVAILABLE) {
                      unavailable.set(true);
                    }
                    return outcome;
                  } finally {
                    slots.release();
                  }
                }));
      }
      for (Future<Outcome> f : futures) {
        try {
          f.get();
        } catch (ExecutionException e) {
          log.error("story processing failed error={}", e.getCause().toString());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }

    boolean capReached = budget.used(today) >= props.dailyCallCap();
    if (unavailable.get() && !capReached) {
      Duration next =
          current
              .map(b -> min(b.delay().multipliedBy(2), props.maxBackoff()))
              .orElse(FIRST_BACKOFF);
      backoff.save(NAME, new Backoff(now.plus(next), next));
      log.warn("story writer unavailable; backing off for {}", next);
    } else if (current.isPresent()) {
      backoff.clear(NAME);
    }
  }

  private StoryInput input(Item item) {
    Optional<Source> source = registry.find(item.sourceId());
    return new StoryInput(
        item.sourceTitle(),
        item.sourceExcerpt(),
        source.map(Source::name).orElse(item.sourceId()),
        source.map(s -> s.region().name()).orElse("GLOBAL"),
        source.flatMap(Source::hint).orElse(null),
        item.publishedAt());
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }
}
