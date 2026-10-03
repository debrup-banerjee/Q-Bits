package com.qbits.ingestion.domain;

/** Where an item is in its life. Ingestion creates PENDING; spec 002 moves it on. */
public enum StoryStatus {
  PENDING,
  PUBLISHED,
  NOT_AI,
  REJECTED,
  EXPIRED
}
