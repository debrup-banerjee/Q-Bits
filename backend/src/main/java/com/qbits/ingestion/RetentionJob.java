package com.qbits.ingestion;

import com.qbits.ingestion.persistence.ItemRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Deletes items (and, later, their summaries) 7 days after publication (spec 001 R9.2). */
@Service
public class RetentionJob {

  private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

  private final ItemRepository items;
  private final IngestionProperties props;
  private final LockingTaskExecutor locks;
  private final Clock clock;

  public RetentionJob(
      ItemRepository items, IngestionProperties props, LockingTaskExecutor locks, Clock clock) {
    this.items = items;
    this.props = props;
    this.locks = locks;
    this.clock = clock;
  }

  public void runOnce() {
    LockConfiguration lock =
        new LockConfiguration(clock.instant(), "retention", Duration.ofMinutes(10), Duration.ZERO);
    locks.executeWithLock(
        (Runnable)
            () -> {
              Instant cutoff = clock.instant().minus(props.retention());
              int deleted = items.deletePublishedBefore(cutoff);
              log.info("retention run cutoff={} deleted={}", cutoff, deleted);
            },
        lock);
  }
}
