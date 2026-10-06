package com.qbits.ingestion.domain;

/** Where an item is in its life. Ingestion creates PENDING; spec 002 moves it on. */
public enum StoryStatus {
  PENDING,
  /** Story written and valid; waits for its daily edition to be published (spec 006). */
  WRITTEN,
  PUBLISHED,
  NOT_AI,
  REJECTED,
  EXPIRED,
  /** Same event as another story, which is published instead (spec 008). */
  DUPLICATE
}
