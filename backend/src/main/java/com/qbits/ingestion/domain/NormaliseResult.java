package com.qbits.ingestion.domain;

/** Outcome of normalising one raw entry. */
public sealed interface NormaliseResult {

  /** The entry is usable. */
  record Accepted(NormalisedEntry entry) implements NormaliseResult {}

  /** The entry has no title or no usable link (R4.5). */
  record Malformed(String reason) implements NormaliseResult {}

  /** The entry was published more than 72 hours before fetch time (R9.1). */
  record TooOld() implements NormaliseResult {}
}
