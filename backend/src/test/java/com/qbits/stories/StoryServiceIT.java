package com.qbits.stories;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.stories.StoryService.CallGate;
import com.qbits.stories.StoryService.Outcome;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.persistence.StoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class StoryServiceIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");
  private static final StoryInput INPUT =
      new StoryInput("Lab shares new AI tool", "Short teaser.", "TechCrunch", "GLOBAL", null, NOW);

  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  @Autowired TransactionTemplate tx;

  private StoryService service(FakeStoryWriter writer) {
    return new StoryService(
        writer, stories, items, StoryFixtures.props(), tx, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void publishesAValidDraft() { // 002 R8.4
    UUID id = item();

    Outcome outcome =
        service(new FakeStoryWriter(StoryFixtures::validDraft))
            .process(id, INPUT, CallGate.UNLIMITED);

    assertThat(outcome).isEqualTo(Outcome.PUBLISHED);
    assertThat(status(id)).isEqualTo(StoryStatus.PUBLISHED);
    assertThat(stories.find(id).orElseThrow())
        .satisfies(
            s -> {
              assertThat(s.promptVersion()).isEqualTo("v1");
              assertThat(s.model()).isEqualTo("test-model");
              assertThat(s.attempts()).isEqualTo(1);
              assertThat(s.writtenAt()).isEqualTo(NOW);
            });
  }

  @Test
  void marksNonAiItemsWithReason() { // 002 R2.1, R2.2
    UUID id = item();

    Outcome outcome =
        service(new FakeStoryWriter(StoryFixtures::notAi)).process(id, INPUT, CallGate.UNLIMITED);

    assertThat(outcome).isEqualTo(Outcome.NOT_AI);
    assertThat(status(id)).isEqualTo(StoryStatus.NOT_AI);
    assertThat(items.findStoryNote(id)).contains("This is about football");
    assertThat(stories.find(id)).isEmpty();
  }

  @Test
  void retriesOnceWithFeedbackThenPublishes() { // 002 R8.2
    UUID id = item();
    FakeStoryWriter writer =
        new FakeStoryWriter(StoryFixtures::validDraft).then(StoryFixtures::invalidDraft);

    Outcome outcome = service(writer).process(id, INPUT, CallGate.UNLIMITED);

    assertThat(outcome).isEqualTo(Outcome.PUBLISHED);
    assertThat(writer.feedback.get(0)).isEmpty();
    assertThat(writer.feedback.get(1))
        .hasValueSatisfying(f -> assertThat(f).contains("SUMMARY_LEN"));
    assertThat(stories.find(id).orElseThrow().attempts()).isEqualTo(2);
  }

  @Test
  void rejectsAfterSecondInvalidDraftAndRecordsRules() { // 002 R8.3
    UUID id = item();

    Outcome outcome =
        service(new FakeStoryWriter(StoryFixtures::invalidDraft))
            .process(id, INPUT, CallGate.UNLIMITED);

    assertThat(outcome).isEqualTo(Outcome.REJECTED);
    assertThat(status(id)).isEqualTo(StoryStatus.REJECTED);
    assertThat(items.findStoryNote(id).orElseThrow())
        .contains("SUMMARY_LEN")
        .contains("SOURCE_NAMED");
    assertThat(stories.find(id)).isEmpty();
  }

  @Test
  void serviceOutageLeavesItemPending() { // 002 R9.2
    UUID id = item();

    Outcome outcome =
        service(new FakeStoryWriter(StoryFixtures::validDraft).thenUnavailable())
            .process(id, INPUT, CallGate.UNLIMITED);

    assertThat(outcome).isEqualTo(Outcome.UNAVAILABLE);
    assertThat(status(id)).isEqualTo(StoryStatus.PENDING);
  }

  @Test
  void providerRejectionMarksRejected() {
    UUID id = item();

    Outcome outcome =
        service(new FakeStoryWriter(StoryFixtures::validDraft).thenRejected())
            .process(id, INPUT, CallGate.UNLIMITED);

    assertThat(outcome).isEqualTo(Outcome.REJECTED);
    assertThat(items.findStoryNote(id).orElseThrow()).startsWith("PROVIDER_ERROR");
  }

  @Test
  void closedGateMakesNoCallAndLeavesPending() { // 002 R9.1
    UUID id = item();
    FakeStoryWriter writer = new FakeStoryWriter(StoryFixtures::validDraft);

    Outcome outcome = service(writer).process(id, INPUT, () -> false);

    assertThat(outcome).isEqualTo(Outcome.UNAVAILABLE);
    assertThat(writer.inputs).isEmpty();
    assertThat(status(id)).isEqualTo(StoryStatus.PENDING);
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

  private StoryStatus status(UUID id) {
    return items.findById(id).orElseThrow().storyStatus();
  }
}
