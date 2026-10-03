package com.qbits.resources;

/** What a host said about a link (spec 005 R4). */
public sealed interface CheckResult {

  /** The project exists. {@code canonicalName} is set when the host reports a new name. */
  record Found(String canonicalName) implements CheckResult {}

  /** The host says it does not exist (or is private/gated). */
  record Missing() implements CheckResult {}

  /** Error, rate limit or timeout. Try again later. */
  record Failed(String reason) implements CheckResult {}

  /** Our own per-host limit is used up for now; no call was made. */
  record Throttled() implements CheckResult {}
}
