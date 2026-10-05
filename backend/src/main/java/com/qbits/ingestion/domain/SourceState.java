package com.qbits.ingestion.domain;

import java.time.Instant;

/**
 * What we remember about a source between fetches. {@code retryAfter} is set while the publisher
 * has asked us to wait (Retry-After) and is null otherwise.
 */
public record SourceState(
    String sourceId,
    String etag,
    String lastModified,
    Instant lastFetchedAt,
    Instant lastSuccessAt,
    int consecutiveFailures,
    SourceHealth health,
    Instant retryAfter) {

  public static SourceState initial(String sourceId) {
    return new SourceState(sourceId, null, null, null, null, 0, SourceHealth.OK, null);
  }

  public SourceState withRetryAfter(Instant until) {
    return new SourceState(
        sourceId,
        etag,
        lastModified,
        lastFetchedAt,
        lastSuccessAt,
        consecutiveFailures,
        health,
        until);
  }

  public SourceState withValidators(String newEtag, String newLastModified) {
    return new SourceState(
        sourceId,
        newEtag,
        newLastModified,
        lastFetchedAt,
        lastSuccessAt,
        consecutiveFailures,
        health,
        retryAfter);
  }
}
