package com.qbits.ingestion.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ItemRepositoryIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired ItemRepository items;

  @Test
  void insertsAndReadsBackAllFields() { // 001 R4.1
    Item item = item("https://example.com/a", "a title", "An excerpt.");

    assertThat(items.insertIfNew(item)).isTrue();

    assertThat(items.findById(item.id())).contains(item);
  }

  @Test
  void secondInsertOfSameCanonicalUrlIsIgnored() { // 001 R6.1
    items.insertIfNew(item("https://example.com/a", "first", null));

    boolean inserted = items.insertIfNew(item("https://example.com/a", "second", null));

    assertThat(inserted).isFalse();
    assertThat(items.countBySource("src")).isEqualTo(1);
  }

  @Test
  void databaseRejectsExcerptLongerThan300Characters() { // 001 non-functional: no body column
    Item tooLong = item("https://example.com/long", "t", "x".repeat(301));

    assertThatThrownBy(() -> items.insertIfNew(tooLong))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void itemsTableHasNoColumnForArticleBodies() { // 001 non-functional
    List<String> columns =
        jdbc.queryForList(
            "select column_name from information_schema.columns where table_name = 'items'",
            String.class);

    assertThat(columns)
        .containsExactlyInAnyOrder(
            "id",
            "source_id",
            "canonical_url",
            "source_title",
            "source_excerpt",
            "title_key",
            "published_at",
            "date_estimated",
            "fetched_at",
            "prefilter_score",
            "story_status",
            "hidden");
  }

  @Test
  void findsSameTitleFromSameSourceWithinWindow() { // 001 R6.2
    items.insertIfNew(item("https://example.com/a", "same title", null));

    assertThat(items.existsSameTitle("src", "same title", NOW.minus(Duration.ofHours(48))))
        .isTrue();
    assertThat(items.existsSameTitle("other", "same title", NOW.minus(Duration.ofHours(48))))
        .isFalse();
    assertThat(items.existsSameTitle("src", "same title", NOW.plusSeconds(1))).isFalse();
  }

  @Test
  void hidesDeletesAndExpiresBySource() { // 001 R8, R9.2
    Item a = item("https://example.com/a", "a", null);
    items.insertIfNew(a);

    assertThat(items.setHiddenForSource("src", true)).isEqualTo(1);
    assertThat(items.findById(a.id()).orElseThrow().hidden()).isTrue();
    assertThat(items.deletePublishedBefore(NOW)).isZero();
    assertThat(items.deletePublishedBefore(NOW.plusSeconds(1))).isEqualTo(1);
  }

  private static Item item(String url, String titleKey, String excerpt) {
    return new Item(
        Ids.newId(),
        "src",
        url,
        "A Title",
        excerpt,
        titleKey,
        NOW,
        false,
        NOW,
        0.5,
        StoryStatus.PENDING,
        false);
  }
}
