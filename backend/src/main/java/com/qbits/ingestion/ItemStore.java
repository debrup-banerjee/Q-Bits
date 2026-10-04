package com.qbits.ingestion;

import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The item operations the stories feature needs (specs 002 and 006), so it never reaches into
 * ingestion's persistence (conventions). Every method joins the caller's transaction when there is
 * one: an edition is published in a single transaction (spec 006 R3.1).
 */
@Service
@Transactional
public class ItemStore {

  private final ItemRepository items;

  public ItemStore(ItemRepository items) {
    this.items = items;
  }

  public Optional<Item> find(UUID id) {
    return items.findById(id);
  }

  /** Ids of a source's items in the given story status (operator rewrites, spec 002 R10.2). */
  public List<UUID> idsBySourceAndStatus(String sourceId, StoryStatus status) {
    return List.copyOf(items.findIdsBySourceAndStatus(sourceId, status));
  }

  /** Visible PENDING items published at or after {@code since}, newest first (spec 002 R1.1). */
  public List<Item> pendingNewestFirst(Instant since, int limit) {
    return List.copyOf(items.findPendingNewestFirst(since, limit));
  }

  /** Marks PENDING items published before {@code cutoff} as EXPIRED (spec 002 R9.3). */
  public int expirePendingBefore(Instant cutoff) {
    return items.expirePendingBefore(cutoff);
  }

  /** Moves an item to a story status, with an optional note of at most 300 characters. */
  public void setStoryStatus(UUID id, StoryStatus status, String note) {
    items.updateStoryStatus(id, status, note);
  }

  /** Puts up to {@code limit} waiting items into an edition, newest first (spec 006 R1.2). */
  public int assignToEdition(UUID editionId, Instant cutoff, Instant since, int limit) {
    return items.assignToEdition(editionId, cutoff, since, limit);
  }

  /** Items of an edition that still wait for a story. */
  public List<Item> pendingInEdition(UUID editionId) {
    return List.copyOf(items.findPendingInEdition(editionId));
  }

  /** Rules the last draft broke, sent with the retry round (spec 006 R2.5). */
  public void setRetryFeedback(UUID id, String feedback) {
    items.setRetryFeedback(id, feedback);
  }

  public Optional<String> retryFeedback(UUID id) {
    return items.findRetryFeedback(id);
  }

  /** Sends one PENDING item back to wait for the next edition (spec 006 R6.2). */
  public void releaseFromEdition(UUID id) {
    items.releaseFromEdition(id);
  }

  /** Every WRITTEN item of the edition becomes PUBLISHED (spec 006 R3.1). Returns the count. */
  public int publishEdition(UUID editionId) {
    return items.publishEdition(editionId);
  }

  /** Items of the edition still PENDING go back for the next edition (spec 006 R3.3). */
  public int releaseUnfinished(UUID editionId) {
    return items.releaseUnfinished(editionId);
  }

  /** How many of the edition's items are in each story status (spec 006 R1.4). */
  public Map<StoryStatus, Integer> countsInEdition(UUID editionId) {
    return items.countByStatusInEdition(editionId);
  }
}
