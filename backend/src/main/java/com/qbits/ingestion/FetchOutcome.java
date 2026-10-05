package com.qbits.ingestion;

/** Result of one conditional GET of a feed, after retries (spec 001 R2, R7.1). */
public sealed interface FetchOutcome {

  /** New content. Validators to send next time, if the server gave any. */
  record Fetched(int httpStatus, byte[] body, String etag, String lastModified)
      implements FetchOutcome {}

  /** 304: nothing changed since the validators we sent (R2.3). */
  record NotModified() implements FetchOutcome {}

  /**
   * Gave up. HTTP status if there was one, a short reason, and the raw {@code Retry-After} value of
   * a 429 or 503 when the server sent one (null otherwise).
   */
  record Failed(Integer httpStatus, String error, String retryAfter) implements FetchOutcome {

    public Failed(Integer httpStatus, String error) {
      this(httpStatus, error, null);
    }
  }
}
