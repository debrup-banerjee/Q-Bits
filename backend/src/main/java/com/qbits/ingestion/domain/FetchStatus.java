package com.qbits.ingestion.domain;

/** Outcome of one attempt to fetch a source (spec 001 R7.2). */
public enum FetchStatus {
  OK,
  NOT_MODIFIED,
  BLOCKED_BY_ROBOTS,
  ROBOTS_UNAVAILABLE,
  FAILED
}
