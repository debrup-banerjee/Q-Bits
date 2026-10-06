package com.qbits.stories.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.FakeStoryGrouper;
import com.qbits.stories.StoryGrouper;
import com.qbits.stories.domain.GroupCandidate;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.Story;
import com.qbits.stories.persistence.StoryRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** The operator clean-up (spec 008 R4), built like EditionAdminIT builds its command. */
class DedupeAdminIT extends IntegrationTest {

  @Autowired EditionDeduplicator deduplicator;
  @Autowired EditionQueries editionQueries;
  @Autowired Clock clock;
  @Autowired ApplicationContext context;
  @Autowired StoryGrouper grouper;
  @Autowired ItemRepository items;
  @Autowired StoryRepository storyRepo;

  @Test
  void holdsBackLiveRepeatsAndPrintsTheGroups() { // 008 R4.1, R4.2
    // Sources unknown to the registry are not official, so the earliest copy is kept.
    UUID first = live("Lab ships watermark tool", Duration.ofHours(5));
    UUID second = live("Lab's new watermark tool is out", Duration.ofHours(2));
    UUID other = live("Chip plant opens", Duration.ofHours(3));
    ((FakeStoryGrouper) grouper)
        .answer(
            cands -> {
              Set<UUID> g =
                  cands.stream()
                      .filter(c -> c.headline().contains("watermark"))
                      .map(GroupCandidate::id)
                      .collect(Collectors.toSet());
              return List.of(g);
            });
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    new DedupeAdminCommands(deduplicator, editionQueries, clock, context)
        .dedupeNow(new PrintStream(out, true, StandardCharsets.UTF_8));

    assertThat(status(first)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(other)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(status(second)).isEqualTo(StoryStatus.DUPLICATE);
    String printed = out.toString(StandardCharsets.UTF_8);
    assertThat(printed)
        .contains("KEPT     " + first)
        .contains("Lab ships watermark tool")
        .contains("  held   " + second)
        .contains("Checked 3 live stories: 1 events with repeats, 1 held back");
  }

  private UUID live(String headline, Duration ago) {
    UUID id = Ids.newId();
    Instant at = Instant.now().minus(ago);
    items.insertIfNew(
        new Item(
            id,
            "example-lab",
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
            "Our summary.",
            List.of(new KeyTerm("Watermark", "A hidden mark.")),
            "m",
            "v3",
            at,
            1,
            1,
            1));
    items.updateStoryStatus(id, StoryStatus.PUBLISHED, null);
    return id;
  }

  @AfterEach
  void resetGrouper() {
    ((FakeStoryGrouper) grouper).answer(c -> List.of());
  }

  private StoryStatus status(UUID id) {
    return StoryStatus.valueOf(
        jdbc.queryForObject("select story_status from items where id = ?", String.class, id));
  }
}
