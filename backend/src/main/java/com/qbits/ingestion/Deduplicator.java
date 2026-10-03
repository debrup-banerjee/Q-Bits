package com.qbits.ingestion;

import com.qbits.ingestion.domain.NormalisedEntry;
import com.qbits.ingestion.persistence.ItemRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Spots items we already have (spec 001 R6.1, R6.2). The unique URL constraint is the backstop. */
@Component
public class Deduplicator {

  static final Duration SAME_TITLE_WINDOW = Duration.ofHours(48);

  private final ItemRepository items;

  public Deduplicator(ItemRepository items) {
    this.items = items;
  }

  public boolean isDuplicate(String sourceId, NormalisedEntry entry, Instant now) {
    return items.existsByCanonicalUrl(entry.canonicalUrl())
        || items.existsSameTitle(sourceId, entry.titleKey(), now.minus(SAME_TITLE_WINDOW));
  }
}
