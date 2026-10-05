package com.qbits.ingestion.domain;

/** Overall health of a source. DEGRADED after repeated failures (spec 001 R7.4). */
public enum SourceHealth {
  OK,
  DEGRADED
}
