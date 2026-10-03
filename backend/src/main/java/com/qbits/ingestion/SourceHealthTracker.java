package com.qbits.ingestion;

import com.qbits.ingestion.domain.FetchStatus;
import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Works out a source's next state after a fetch attempt. After N failures in a row the source is
 * DEGRADED and a warning is logged; one success clears it (spec 001 R7.4).
 */
@Component
public class SourceHealthTracker {

  private static final Logger log = LoggerFactory.getLogger(SourceHealthTracker.class);

  private final int degradedAfter;

  public SourceHealthTracker(IngestionProperties props) {
    this.degradedAfter = props.degradedAfterFailures();
  }

  public SourceState next(SourceState state, FetchStatus status, Instant at) {
    boolean success = status == FetchStatus.OK || status == FetchStatus.NOT_MODIFIED;
    boolean failure = status == FetchStatus.FAILED;

    int failures =
        success ? 0 : failure ? state.consecutiveFailures() + 1 : state.consecutiveFailures();
    SourceHealth health = state.health();
    if (success) {
      health = SourceHealth.OK;
    } else if (failures >= degradedAfter) {
      health = SourceHealth.DEGRADED;
    }
    if (health == SourceHealth.DEGRADED && state.health() != SourceHealth.DEGRADED) {
      log.warn("source degraded source={} consecutiveFailures={}", state.sourceId(), failures);
    } else if (health == SourceHealth.OK && state.health() == SourceHealth.DEGRADED) {
      log.info("source recovered source={}", state.sourceId());
    }
    return new SourceState(
        state.sourceId(),
        state.etag(),
        state.lastModified(),
        at,
        success ? at : state.lastSuccessAt(),
        failures,
        health);
  }
}
