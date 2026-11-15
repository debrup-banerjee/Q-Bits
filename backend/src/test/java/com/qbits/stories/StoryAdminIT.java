package com.qbits.stories;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.common.Ids;
import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.ingestion.persistence.ItemRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.stories.StoryService.CallGate;
import com.qbits.stories.StoryService.Outcome;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.persistence.StoryRepository;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

class StoryAdminIT extends IntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired ItemRepository items;
  @Autowired StoryRepository stories;
  @Autowired WriterBudgetRepository budget;
  @Autowired TransactionTemplate tx;
  @Autowired ApplicationContext context;

  @Test
  void rewritesAStoryWithTheCurrentPromptVersion() { // 002 R10.2
    UUID id = item("src-a");
    service("v1")
        .process(id, new StoryInput("T", "E", "Src", "GLOBAL", null, NOW), CallGate.UNLIMITED);
    assertThat(stories.find(id).orElseThrow().promptVersion()).isEqualTo("v1");

    Outcome outcome = admin("v2").rewriteStory(id);

    assertThat(outcome).isEqualTo(Outcome.PUBLISHED);
    assertThat(stories.find(id).orElseThrow().promptVersion()).isEqualTo("v2");
  }

  @Test
  void rewritesEveryPublishedStoryOfASource() { // 002 R10.2
    UUID a = item("src-a");
    UUID b = item("src-a");
    UUID other = item("src-b");
    for (UUID id : List.of(a, b, other)) {
      service("v1")
          .process(id, new StoryInput("T", "E", "Src", "GLOBAL", null, NOW), CallGate.UNLIMITED);
    }

    Map<Outcome, Integer> result = admin("v2").rewriteSource("src-a");

    assertThat(result).containsEntry(Outcome.PUBLISHED, 2);
    assertThat(stories.find(a).orElseThrow().promptVersion()).isEqualTo("v2");
    assertThat(stories.find(other).orElseThrow().promptVersion()).isEqualTo("v1");
  }

  private StoryService service(String version) {
    return new StoryService(
        new FakeStoryWriter(StoryFixtures::validDraft),
        stories,
        new ItemStore(items),
        props(version),
        tx,
        clock);
  }

  private StoryAdminCommands admin(String version) {
    return new StoryAdminCommands(
        new ItemStore(items),
        new SourceRegistry(List.of()),
        service(version),
        budget,
        props(version),
        clock,
        context);
  }

  private static StoryWriterProperties props(String version) {
    return new StoryWriterProperties(
        StoryWriterProperties.Mode.DIGEST,
        "anthropic",
        "test-model",
        version,
        800,
        500,
        4,
        20,
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(30));
  }

  private UUID item(String sourceId) {
    UUID id = Ids.newId();
    items.insertIfNew(
        new Item(
            id,
            sourceId,
            "https://a.example/" + id,
            "T",
            "E",
            id.toString(),
            NOW,
            false,
            NOW,
            1.0,
            StoryStatus.PENDING,
            false));
    return id;
  }
}
