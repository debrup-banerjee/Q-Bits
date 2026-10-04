package com.qbits.stories.digest;

import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.StoryStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes a whole edition visible in one transaction (spec 006 R3.1). The edition's counts are taken
 * from its items' statuses at that moment, so a step that ran twice never counts twice or loses a
 * count (R1.4).
 */
@Component
public class EditionPublisher {

  private static final Logger log = LoggerFactory.getLogger(EditionPublisher.class);

  private final ItemStore items;
  private final EditionRepository editions;
  private final TransactionTemplate tx;

  public EditionPublisher(ItemStore items, EditionRepository editions, TransactionTemplate tx) {
    this.items = items;
    this.editions = editions;
    this.tx = tx;
  }

  public int publish(UUID editionId, Instant at) {
    Integer published =
        tx.execute(
            status -> {
              items.publishEdition(editionId);
              int released = items.releaseUnfinished(editionId);
              Map<StoryStatus, Integer> counts = items.countsInEdition(editionId);
              int count = counts.getOrDefault(StoryStatus.PUBLISHED, 0);
              editions.markPublished(
                  editionId,
                  count,
                  counts.getOrDefault(StoryStatus.NOT_AI, 0),
                  counts.getOrDefault(StoryStatus.REJECTED, 0),
                  at);
              if (released > 0) {
                log.warn(
                    "edition published with unfinished items edition={} released={}",
                    editionId,
                    released);
              }
              return count;
            });
    log.info("edition published edition={} stories={}", editionId, published);
    return published == null ? 0 : published;
  }
}
