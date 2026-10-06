package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.catalog.CatalogService;
import com.qbits.common.Ids;
import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Region;
import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourceType;
import com.qbits.stories.DedupeProperties;
import com.qbits.stories.FakeStoryGrouper;
import com.qbits.stories.StoryFixtures;
import com.qbits.stories.StoryWriter;
import com.qbits.stories.domain.GroupCandidate;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.Story;
import com.qbits.stories.persistence.StoryRepository;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class EditionDeduplicatorIT extends IntegrationTest {

  /** 06:00 IST on 4 October 2026. */
  private static final Instant CUTOFF = Instant.parse("2026-10-04T00:30:00Z");

  private static final Instant NOW = CUTOFF.plus(Duration.ofHours(1));

  @Autowired ItemRepository items;
  @Autowired StoryRepository storyRepo;
  @Autowired EditionRepository editions;
  @Autowired WriterBudgetRepository budget;
  @Autowired TransactionTemplate tx;
  @Autowired CatalogService catalog;

  private FakeStoryGrouper grouper;
  private UUID edition;

  @BeforeEach
  void setUp() {
    grouper = new FakeStoryGrouper();
    edition = Ids.newId();
    editions.create(
        new Edition(
            edition,
            CUTOFF,
            EditionStatus.SUBMITTED,
            null,
            null,
            0,
            0,
            0,
            0,
            CUTOFF,
            null,
            CUTOFF,
            null));
  }

  private EditionDeduplicator deduplicator(boolean enabled) {
    SourceRegistry registry =
        new SourceRegistry(
            List.of(
                source("openai-news", "OpenAI", true),
                source("the-verge-ai", "The Verge", false),
                source("techcrunch-ai", "TechCrunch", false),
                source("bbc-technology", "BBC News", false)));
    ItemStore store = new ItemStore(items);
    return new EditionDeduplicator(
        new EditionPublisher(store, editions, tx),
        editions,
        storyRepo,
        store,
        registry,
        grouper,
        budget,
        StoryFixtures.props(),
        new DedupeProperties(enabled, "v1", Duration.ofSeconds(60), 1000),
        new DigestProperties(
            LocalTime.of(6, 0),
            ZoneId.of("Asia/Kolkata"),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(6),
            Duration.ofHours(2)),
        tx);
  }

  /** Groups the stories whose headline contains "watermark". */
  private static List<Set<UUID>> watermarkGroup(List<GroupCandidate> candidates) {
    Set<UUID> group =
        candidates.stream()
            .filter(c -> c.headline().toLowerCase().contains("watermark"))
            .map(GroupCandidate::id)
            .collect(Collectors.toSet());
    return group.size() >= 2 ? List.of(group) : List.of();
  }

  @Test
  void publishesOneStoryPerEvent() { // 008 R1.1, R2.1, R2.3, R2.5
    UUID openai = written("openai-news", "OpenAI explains EU text watermarking", 6);
    UUID verge = written("the-verge-ai", "OpenAI rolls out text watermarks", 3);
    UUID techcrunch = written("techcrunch-ai", "OpenAI to watermark ChatGPT text", 2);
    UUID other = written("bbc-technology", "Chip plant opens", 4);
    grouper.answer(EditionDeduplicatorIT::watermarkGroup);

    boolean published = deduplicator(true).publish(edition(), NOW);

    assertThat(published).isTrue();
    assertThat(status(openai)).isEqualTo(StoryStatus.PUBLISHED); // official, though not earliest
    assertThat(status(other)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(verge)).isEqualTo(StoryStatus.DUPLICATE);
    assertThat(status(techcrunch)).isEqualTo(StoryStatus.DUPLICATE);
    assertThat(duplicateOf(verge)).contains(openai);
    assertThat(duplicateOf(techcrunch)).contains(openai);
    Map<String, Object> e = editionRow();
    assertThat(e.get("status")).isEqualTo("PUBLISHED");
    assertThat(e.get("published")).isEqualTo(2);
    assertThat(e.get("duplicates")).isEqualTo(2);
  }

  @Test
  void checksNewStoriesAgainstLiveOnes() { // 008 R1.1, R2.2
    UUID live = published("the-verge-ai", "OpenAI rolls out text watermarks", 20);
    UUID openai = written("openai-news", "OpenAI explains EU text watermarking", 2);
    grouper.answer(EditionDeduplicatorIT::watermarkGroup);

    deduplicator(true).publish(edition(), NOW);

    assertThat(grouper.calls().getFirst())
        .extracting(GroupCandidate::id, GroupCandidate::published)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(live, true),
            org.assertj.core.groups.Tuple.tuple(openai, false));
    assertThat(status(live)).isEqualTo(StoryStatus.PUBLISHED); // never withdrawn
    assertThat(status(openai)).isEqualTo(StoryStatus.DUPLICATE);
    assertThat(duplicateOf(openai)).contains(live);
  }

  @Test
  void duplicatesAreNeverShown() { // 008 R2.4
    UUID openai = written("openai-news", "OpenAI explains EU text watermarking", 6);
    UUID verge = written("the-verge-ai", "OpenAI rolls out text watermarks", 3);
    grouper.answer(EditionDeduplicatorIT::watermarkGroup);

    deduplicator(true).publish(edition(), NOW);

    assertThat(catalog.find(openai)).isPresent();
    assertThat(catalog.find(verge)).isEmpty();
    assertThat(
            catalog
                .list(Optional.empty(), Optional.empty(), 100, Duration.ofHours(72))
                .data()
                .stream()
                .map(s -> s.id()))
        .contains(openai)
        .doesNotContain(verge);
    assertThat(catalog.counts().get(Section.GLOBAL_AI_TECH)).isEqualTo(1L);
  }

  @Test
  void aFailedCallIsRetriedBeforeTheDeadline() { // 008 R3.1
    UUID a = written("openai-news", "OpenAI explains EU text watermarking", 6);
    grouper.failWith(new StoryWriter.WriterUnavailable("HTTP 529", null));

    boolean published = deduplicator(true).publish(edition(), NOW);

    assertThat(published).isFalse();
    assertThat(status(a)).isEqualTo(StoryStatus.WRITTEN);
    assertThat(editionRow().get("status")).isEqualTo("SUBMITTED");
    assertThat(editionRow().get("dedupe_attempts")).isEqualTo(1);

    grouper.answer(c -> List.of());
    assertThat(deduplicator(true).publish(edition(), NOW.plusSeconds(600))).isTrue();
    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void nearTheDeadlineItPublishesWithoutGrouping() { // 008 R3.1
    UUID a = written("openai-news", "OpenAI explains EU text watermarking", 6);
    UUID b = written("the-verge-ai", "OpenAI rolls out text watermarks", 3);
    grouper.failWith(new StoryWriter.WriterRejected("HTTP 400", null));
    Instant nearDeadline = CUTOFF.plus(Duration.ofHours(6)).minus(Duration.ofMinutes(2));

    boolean published = deduplicator(true).publish(edition(), nearDeadline);

    assertThat(published).isTrue();
    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(b)).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void withNoCallsLeftItPublishesWithoutGrouping() { // 008 R3.2
    UUID a = written("openai-news", "OpenAI explains EU text watermarking", 6);
    LocalDate today = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
    int cap = StoryFixtures.props().dailyCallCap();
    budget.takeUpTo(today, cap, cap);

    assertThat(deduplicator(true).publish(edition(), NOW)).isTrue();

    assertThat(grouper.calls()).isEmpty();
    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void takesOneCallFromTheDailyBudget() { // 008 R3.2
    written("openai-news", "OpenAI explains EU text watermarking", 6);
    LocalDate today = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
    int before = budget.used(today);

    deduplicator(true).publish(edition(), NOW);

    assertThat(budget.used(today)).isEqualTo(before + 1);
  }

  @Test
  void switchedOffOrNothingNewMeansNoCall() { // 008 config switch
    UUID a = written("openai-news", "OpenAI explains EU text watermarking", 6);

    deduplicator(false).publish(edition(), NOW);

    assertThat(grouper.calls()).isEmpty();
    assertThat(status(a)).isEqualTo(StoryStatus.PUBLISHED);
  }

  @Test
  void cleanUpHoldsBackLiveRepeats() { // 008 R4.1
    UUID verge = published("the-verge-ai", "OpenAI rolls out text watermarks", 10);
    UUID openai = published("openai-news", "OpenAI explains EU text watermarking", 5);
    UUID techcrunch = published("techcrunch-ai", "OpenAI to watermark ChatGPT text", 8);
    UUID other = published("bbc-technology", "Chip plant opens", 4);
    grouper.answer(EditionDeduplicatorIT::watermarkGroup);

    EditionDeduplicator.CleanUp result = deduplicator(true).cleanUpPublished(CUTOFF, NOW);

    assertThat(result.heldBack()).isEqualTo(Map.of(verge, openai, techcrunch, openai));
    assertThat(status(openai)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(other)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(verge)).isEqualTo(StoryStatus.DUPLICATE);
    assertThat(status(techcrunch)).isEqualTo(StoryStatus.DUPLICATE);
  }

  private Edition edition() {
    return editions.find(edition).orElseThrow();
  }

  /** A written story in this edition, published at the source {@code hoursBefore} the cut-off. */
  private UUID written(String source, String headline, int hoursBefore) {
    UUID id = story(source, headline, hoursBefore);
    jdbc.update(
        "update items set story_status = 'WRITTEN', edition_id = ? where id = ?", edition, id);
    return id;
  }

  /** A story already live from an earlier edition. */
  private UUID published(String source, String headline, int hoursBefore) {
    UUID id = story(source, headline, hoursBefore);
    jdbc.update("update items set story_status = 'PUBLISHED' where id = ?", id);
    return id;
  }

  private UUID story(String source, String headline, int hoursBefore) {
    UUID id = Ids.newId();
    Instant at = CUTOFF.minus(Duration.ofHours(hoursBefore));
    items.insertIfNew(
        new Item(
            id,
            source,
            "https://news.example/" + id,
            "SOURCE TITLE",
            "SOURCE EXCERPT",
            id.toString(),
            at,
            false,
            at,
            1.0,
            StoryStatus.PENDING,
            false));
    storyRepo.save(
        new Story(
            id,
            Section.GLOBAL_AI_TECH,
            headline,
            "Our summary of " + headline + ".",
            List.of(new KeyTerm("Watermark", "A hidden mark.")),
            "m",
            "v3",
            at,
            1,
            1,
            1));
    return id;
  }

  private StoryStatus status(UUID id) {
    return StoryStatus.valueOf(
        jdbc.queryForObject("select story_status from items where id = ?", String.class, id));
  }

  private Optional<UUID> duplicateOf(UUID id) {
    return Optional.ofNullable(
        jdbc.queryForObject("select duplicate_of from items where id = ?", UUID.class, id));
  }

  private Map<String, Object> editionRow() {
    return jdbc.queryForMap(
        "select status, published, duplicates, dedupe_attempts from editions where id = ?",
        edition);
  }

  private static Source source(String id, String name, boolean official) {
    URI uri = URI.create("https://" + id + ".example");
    return new Source(
        id,
        name,
        uri,
        uri,
        SourceType.RSS,
        uri,
        LocalDate.of(2026, 10, 4),
        true,
        true,
        Region.GLOBAL,
        null,
        null,
        official);
  }
}
