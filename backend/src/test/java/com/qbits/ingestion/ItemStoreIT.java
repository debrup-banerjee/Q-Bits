package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ItemStoreIT extends IntegrationTest {

  private static final Instant CUTOFF = Instant.parse("2026-10-04T00:30:00Z");

  @Autowired ItemStore store;
  @Autowired ItemRepository items;
  @Autowired TransactionTemplate tx;

  @Test
  void joinsTheCallersTransaction() { // 006 R3.1: an edition is published in one transaction
    UUID edition = edition();
    UUID a = item();
    store.assignToEdition(edition, CUTOFF, CUTOFF.minus(Duration.ofHours(72)), 10);
    store.setStoryStatus(a, StoryStatus.WRITTEN, null);

    tx.executeWithoutResult(
        status -> {
          assertThat(store.publishEdition(edition)).isEqualTo(1);
          status.setRollbackOnly(); // the caller's transaction fails after the store's write
        });

    assertThat(store.find(a).orElseThrow().storyStatus()).isEqualTo(StoryStatus.WRITTEN);

    tx.executeWithoutResult(status -> store.publishEdition(edition));
    assertThat(store.find(a).orElseThrow().storyStatus()).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void retryFeedbackIsClearedWhenAnItemLeavesPending() { // 006 R2.5
    UUID written = item();
    UUID rejected = item();
    UUID expired = item();
    UUID waiting = item();
    for (UUID id : new UUID[] {written, rejected, expired, waiting}) {
      store.setRetryFeedback(id, "SUMMARY_LEN: too short");
    }

    store.setStoryStatus(written, StoryStatus.WRITTEN, null);
    store.setStoryStatus(rejected, StoryStatus.REJECTED, "SUMMARY_LEN");
    jdbc.update(
        "update items set published_at = ? where id = ?",
        Timestamp.from(CUTOFF.minus(Duration.ofHours(80))),
        expired);
    store.expirePendingBefore(CUTOFF.minus(Duration.ofHours(72)));
    store.setStoryStatus(waiting, StoryStatus.PENDING, null);

    assertThat(store.retryFeedback(written)).isEmpty();
    assertThat(store.retryFeedback(rejected)).isEmpty();
    assertThat(store.find(expired).orElseThrow().storyStatus()).isEqualTo(StoryStatus.EXPIRED);
    assertThat(store.retryFeedback(expired)).isEmpty();
    assertThat(store.retryFeedback(waiting)).contains("SUMMARY_LEN: too short");
  }

  @Test
  void countsAnEditionsItemsByStatus() { // 006 R1.4
    UUID edition = edition();
    UUID a = item();
    UUID b = item();
    UUID c = item();
    store.assignToEdition(edition, CUTOFF, CUTOFF.minus(Duration.ofHours(72)), 10);
    store.setStoryStatus(a, StoryStatus.NOT_AI, "Sport");
    store.setStoryStatus(b, StoryStatus.REJECTED, "NUMBERS");

    assertThat(store.countsInEdition(edition))
        .isEqualTo(Map.of(StoryStatus.NOT_AI, 1, StoryStatus.REJECTED, 1, StoryStatus.PENDING, 1));
    assertThat(store.pendingInEdition(edition)).extracting(Item::id).containsExactly(c);
  }

  @Test
  void aNoteAlwaysFitsItsColumn() { // 002 R2.2
    UUID id = item();

    store.setStoryStatus(id, StoryStatus.NOT_AI, "a\u0000b" + "x".repeat(297) + "😀z");

    String note = items.findStoryNote(id).orElseThrow();
    assertThat(note.codePointCount(0, note.length())).isEqualTo(300);
    assertThat(note).startsWith("abx").endsWith("😀");
  }

  private UUID edition() {
    UUID id = Ids.newId();
    jdbc.update(
        "insert into editions (id, cutoff_at, status, created_at) values (?, ?, 'COLLECTING', ?)",
        id,
        Timestamp.from(CUTOFF),
        Timestamp.from(CUTOFF));
    return id;
  }

  private UUID item() {
    UUID id = Ids.newId();
    Instant at = CUTOFF.minus(Duration.ofHours(2));
    items.insertIfNew(
        new Item(
            id,
            "src",
            "https://a.example/" + id,
            "T",
            "E",
            id.toString(),
            at,
            false,
            at,
            1.0,
            StoryStatus.PENDING,
            false));
    return id;
  }
}
