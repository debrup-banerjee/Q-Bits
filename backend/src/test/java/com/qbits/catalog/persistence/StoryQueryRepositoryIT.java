package com.qbits.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.catalog.CatalogFixtures;
import com.qbits.catalog.CatalogService;
import com.qbits.catalog.domain.Cursor;
import com.qbits.catalog.domain.StoryPage;
import com.qbits.catalog.domain.StoryView;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.stories.domain.Section;
import com.qbits.stories.persistence.StoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoryQueryRepositoryIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired StoryQueryRepository queries;
  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  @Autowired com.qbits.stories.digest.EditionRepository editionRepo;
  @Autowired com.qbits.stories.digest.DigestProperties digestProps;
  @Autowired com.qbits.resources.ResourceQueries resourceQueries;

  private CatalogFixtures seed;
  private CatalogService catalog;

  @BeforeEach
  void setUp() {
    seed = new CatalogFixtures(items, stories);
    catalog =
        new CatalogService(
            queries,
            new SourceRegistry(List.of()),
            resourceQueries,
            new com.qbits.stories.digest.EditionQueries(
                editionRepo,
                digestProps,
                com.qbits.stories.StoryFixtures.props(),
                Clock.fixed(NOW, ZoneOffset.UTC)));
  }

  @Test
  void includesExactly72HoursAndExcludesOlder() { // 003 R2.1
    UUID edge = seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofHours(72)));
    UUID old =
        seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofHours(72)).minusSeconds(1));

    List<UUID> ids =
        catalog.list(Optional.empty(), Optional.empty(), 20).data().stream()
            .map(StoryView::id)
            .toList();

    assertThat(ids).contains(edge).doesNotContain(old);
  }

  @Test
  void showsOnlyPublishedVisibleStories() { // 003 R2.1
    UUID shown = seed.published("src", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    UUID hidden = seed.published("off", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    items.setHiddenForSource("off", true);
    seed.withStatus("src", StoryStatus.NOT_AI, NOW.minusSeconds(30));
    seed.withStatus("src", StoryStatus.REJECTED, NOW.minusSeconds(30));
    seed.withStatus("src", StoryStatus.PENDING, NOW.minusSeconds(30));

    List<UUID> ids =
        catalog.list(Optional.empty(), Optional.empty(), 20).data().stream()
            .map(StoryView::id)
            .toList();

    assertThat(ids).containsExactly(shown).doesNotContain(hidden);
  }

  @Test
  void ordersNewestFirstAndFiltersBySection() { // 003 R2.1, R2.2
    UUID a = seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofHours(3)));
    UUID b = seed.published("src", Section.WORLD_BUSINESS, NOW.minus(Duration.ofHours(2)));
    UUID c = seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofHours(1)));

    assertThat(ids(catalog.list(Optional.empty(), Optional.empty(), 20))).containsExactly(c, b, a);
    assertThat(ids(catalog.list(Optional.of(Section.INDIA_AI), Optional.empty(), 20)))
        .containsExactly(c, a);
  }

  @Test
  void pagesWithCursorWithoutGapsOrRepeats() { // 003 R2.1
    List<UUID> all = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      all.add(0, seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofMinutes(10 - i))));
    }
    // two stories at the same instant to exercise the id tie-break
    all.add(seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofMinutes(30))));
    all.add(seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofMinutes(30))));

    List<UUID> seen = new ArrayList<>();
    Optional<Cursor> cursor = Optional.empty();
    do {
      StoryPage page = catalog.list(Optional.empty(), cursor, 3);
      seen.addAll(ids(page));
      cursor =
          Optional.ofNullable(page.nextCursor())
              .flatMap(com.qbits.catalog.domain.CursorCodec::decode);
    } while (cursor.isPresent());

    assertThat(seen).hasSize(9).doesNotHaveDuplicates().containsAll(all);
  }

  @Test
  void viewHasOurFieldsAndNeverTheSourceText() { // 003 R2.3, R2.4
    UUID id = seed.published("src", Section.INDIA_AI, NOW.minusSeconds(60));

    StoryView v = catalog.find(id).orElseThrow();

    assertThat(v.section().slug()).isEqualTo("india-ai");
    assertThat(v.section().name()).isEqualTo("India AI");
    assertThat(v.originalUrl()).startsWith("https://news.example/");
    assertThat(v.attribution()).isEqualTo("Summary written from src's headline and teaser");
    assertThat(v.toString()).doesNotContain("SOURCE TITLE", "SOURCE EXCERPT");
  }

  @Test
  void countsBySectionAndReportsLatest() { // 003 R1.1, R2.7
    seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofHours(2)));
    seed.published("src", Section.INDIA_AI, NOW.minus(Duration.ofHours(1)));

    assertThat(catalog.counts())
        .containsEntry(Section.INDIA_AI, 2L)
        .containsEntry(Section.WORLD_BUSINESS, 0L);
    assertThat(catalog.dataAsOf()).contains(NOW.minus(Duration.ofHours(1)));
  }

  @Test
  void coverageCountsDistinctDuplicateSources() { // 009 R1.1
    UUID kept = seed.published("a", Section.GLOBAL_AI_TECH, NOW.minusSeconds(600));
    heldBack("b", kept);
    heldBack("c", kept);
    UUID alone = seed.published("d", Section.GLOBAL_AI_TECH, NOW.minusSeconds(500));

    var all = queries.coverage(NOW.minus(Duration.ofHours(24)), 1);

    assertThat(all)
        .filteredOn(c -> c.id().equals(kept))
        .extracting(c -> c.coverage())
        .containsExactly(3);
    assertThat(all)
        .filteredOn(c -> c.id().equals(alone))
        .extracting(c -> c.coverage())
        .containsExactly(1);
    assertThat(queries.coverage(NOW.minus(Duration.ofHours(24)), 2))
        .extracting(c -> c.id())
        .containsExactly(kept);
  }

  @Test
  void severalCopiesFromOneSourceCountOnce() { // 009 R1.1
    UUID kept = seed.published("a", Section.GLOBAL_AI_TECH, NOW.minusSeconds(600));
    heldBack("b", kept);
    heldBack("b", kept);
    heldBack("a", kept);

    assertThat(queries.coverage(NOW.minus(Duration.ofHours(24)), 1))
        .filteredOn(c -> c.id().equals(kept))
        .extracting(c -> c.coverage())
        .containsExactly(2);
  }

  @Test
  void coverageRisesWhenLaterDuplicatesArriveAndIsStableOnReread() { // 009 R1.2, R1.3
    UUID kept = seed.published("a", Section.GLOBAL_AI_TECH, NOW.minusSeconds(600));
    heldBack("b", kept);
    Instant from = NOW.minus(Duration.ofHours(24));

    assertThat(queries.coverage(from, 1)).isEqualTo(queries.coverage(from, 1));
    int before = queries.coverage(from, 1).getFirst().coverage();
    heldBack("c", kept);

    assertThat(queries.coverage(from, 1).getFirst().coverage()).isEqualTo(before + 1);
  }

  @Test
  void coverageIgnoresHiddenAndOutOfWindowStories() { // 009 R2.4
    UUID old = seed.published("a", Section.GLOBAL_AI_TECH, NOW.minus(Duration.ofHours(30)));
    heldBack("b", old);
    UUID hidden = seed.published("off", Section.GLOBAL_AI_TECH, NOW.minusSeconds(60));
    heldBack("b", hidden);
    items.setHiddenForSource("off", true);

    assertThat(queries.coverage(NOW.minus(Duration.ofHours(24)), 1)).isEmpty();
  }

  private void heldBack(String sourceId, UUID keptId) {
    UUID dup = seed.withStatus(sourceId, StoryStatus.PUBLISHED, NOW.minusSeconds(300));
    assertThat(items.markDuplicate(dup, keptId)).isTrue();
  }

  private static List<UUID> ids(StoryPage page) {
    return page.data().stream().map(StoryView::id).toList();
  }
}
