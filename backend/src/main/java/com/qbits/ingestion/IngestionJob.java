package com.qbits.ingestion;

import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One ingestion run over all enabled sources. Each source runs on its own virtual thread, so a slow
 * or failing feed never holds up the others (R7.3). A database lock makes sure only one app
 * instance runs at a time (R2.5).
 */
@Service
public class IngestionJob {

  private static final Logger log = LoggerFactory.getLogger(IngestionJob.class);
  static final String LOCK_NAME = "ingestion";

  private final SourceRegistry registry;
  private final SourceIngestor ingestor;
  private final LockingTaskExecutor locks;

  public IngestionJob(SourceRegistry registry, SourceIngestor ingestor, LockingTaskExecutor locks) {
    this.registry = registry;
    this.ingestor = ingestor;
    this.locks = locks;
  }

  /** Runs once if no other instance holds the lock. Returns true if this call did the work. */
  public boolean runOnce() {
    LockConfiguration lock =
        new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(30), Duration.ZERO);
    try {
      return locks.executeWithLock(this::runAllSources, lock).wasExecuted();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException("ingestion run failed", t);
    }
  }

  private int runAllSources() {
    List<Source> sources = registry.enabled();
    int ran = 0;
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<SourceIngestor.Result>> futures = new ArrayList<>();
      for (Source source : sources) {
        futures.add(pool.submit(() -> ingestor.ingestIfDue(source)));
      }
      for (Future<SourceIngestor.Result> f : futures) {
        try {
          if (f.get() == SourceIngestor.Result.DONE) {
            ran++;
          }
        } catch (ExecutionException e) {
          log.error("unexpected source failure error={}", e.getCause().toString());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    log.info("ingestion run finished sources={} fetched={}", sources.size(), ran);
    return ran;
  }
}
