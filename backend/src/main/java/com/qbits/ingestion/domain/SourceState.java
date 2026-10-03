package com.qbits.ingestion.domain;

import java.time.Instant;

/** What we remember about a source between fetches. */
public record SourceState(
    String sourceId,
    String etag,
    String lastModified,
    Instant lastFetchedAt,
    Instant lastSuccessAt,
    int consecutiveFailures,
    SourceHealth health) {

  public static SourceState initial(String sourceId) {
    return new SourceState(sourceId, null, null, null, null, 0, SourceHealth.OK);
  }
}
