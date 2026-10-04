package com.qbits.stories.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.Story;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class StoryRepositoryIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

  @Autowired StoryRepository stories;
  @Autowired ItemRepository items;

  @Test
  void savesAndLoadsKeyTermsAsJson() { // 002 R5.1, R10.1
    UUID id = item();
    Story story = story(id, Section.INDIA_AI, "Headline");

    stories.save(story);

    assertThat(stories.find(id)).contains(story);
  }

  @Test
  void savingAgainReplacesTheStory() { // 002 R10.2
    UUID id = item();
    stories.save(story(id, Section.GLOBAL_AI_TECH, "First"));

    stories.save(story(id, Section.WORLD_BUSINESS, "Second"));

    assertThat(stories.find(id).orElseThrow().headline()).isEqualTo("Second");
  }

  @Test
  void databaseOnlyAcceptsTheFourSections() { // 002 R3.1
    UUID id = item();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into stories (item_id, section, headline, summary, model,"
                        + " prompt_version, written_at, input_tokens, output_tokens, attempts)"
                        + " values (?, 'SPORTS', 'h', 's', 'm', 'v1', now(), 1, 1, 1)",
                    id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingAnItemDeletesItsStory() { // 001 R9.2 with 002
    UUID id = item();
    stories.save(story(id, Section.INDIA_AI, "H"));

    items.deletePublishedBefore(NOW.plusSeconds(1));

    assertThat(stories.find(id)).isEmpty();
  }

  @Test
  void updatesItemStatusAndNote() {
    UUID id = item();

    items.updateStoryStatus(id, StoryStatus.NOT_AI, "About football");

    assertThat(items.findById(id).orElseThrow().storyStatus()).isEqualTo(StoryStatus.NOT_AI);
    assertThat(items.findStoryNote(id)).contains("About football");
  }

  @Test
  void sectionSlugsRoundTrip() {
    for (Section s : Section.values()) {
      assertThat(Section.fromSlug(s.slug())).contains(s);
    }
    assertThat(Section.fromSlug("business")).isEmpty();
  }

  private UUID item() {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            "src",
            "https://a.example/" + id,
            "T",
            "E",
            "t",
            NOW,
            false,
            NOW,
            1.0,
            StoryStatus.PENDING,
            false));
    return id;
  }

  private static Story story(UUID id, Section section, String headline) {
    return new Story(
        id,
        section,
        headline,
        "Summary text.",
        List.of(new KeyTerm("GPU", "A chip that does many small sums at once.")),
        "model-x",
        "v1",
        NOW,
        120,
        300,
        1);
  }
}
