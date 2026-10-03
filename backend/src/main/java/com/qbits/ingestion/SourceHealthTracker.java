package com.qbits.ingestion;

import com.qbits.ingestion.domain.FetchStatus;
import com.qbits.ingestion.domain.SourceState;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Works out a source's next state after a fetch attempt. */
@Component
public class SourceHealthTracker {

  public SourceState next(SourceState state, FetchStatus status, Instant at) {
    boolean success = status == FetchStatus.OK || status == FetchStatus.NOT_MODIFIED;
    boolean failure = status == FetchStatus.FAILED;
    return new SourceState(
        state.sourceId(),
        state.etag(),
        state.lastModified(),
        at,
        success ? at : state.lastSuccessAt(),
        success ? 0 : failure ? state.consecutiveFailures() + 1 : state.consecutiveFailures(),
        state.health());
  }
}
