package com.qbits.ingestion;

/** Result of one conditional GET of a feed, after retries (spec 001 R2, R7.1). */
public sealed interface FetchOutcome {

  /** New content. Validators to send next time, if the server gave any. */
  record Fetched(int httpStatus, byte[] body, String etag, String lastModified)
      implements FetchOutcome {}

  /** 304: nothing changed since the validators we sent (R2.3). */
  record NotModified() implements FetchOutcome {}

  /** Gave up. HTTP status if there was one, and a short reason. */
  record Failed(Integer httpStatus, String error) implements FetchOutcome {}
}
