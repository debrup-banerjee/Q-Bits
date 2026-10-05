package com.qbits.stories;

import com.qbits.ingestion.ItemStore;
import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import com.qbits.stories.StoryService.Outcome;
import com.qbits.stories.domain.StoryInput;
import com.qbits.stories.persistence.WriterBudgetRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Operator commands to rewrite stories with the current prompt (spec 002 R10.2), run with the
 * {@code admin} profile:
 *
 * <pre>
 * --rewrite-story=&lt;item id&gt;
 * --rewrite-source=&lt;source id&gt;
 * </pre>
 *
 * Calls count against the daily budget. Then the app exits.
 */
@Component
@Profile("admin")
public class StoryAdminCommands implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StoryAdminCommands.class);

  private final ItemStore items;
  private final SourceRegistry registry;
  private final StoryService service;
  private final WriterBudgetRepository budget;
  private final StoryWriterProperties props;
  private final Clock clock;
  private final ApplicationContext context;

  public StoryAdminCommands(
      ItemStore items,
      SourceRegistry registry,
      StoryService service,
      WriterBudgetRepository budget,
      StoryWriterProperties props,
      Clock clock,
      ApplicationContext context) {
    this.items = items;
    this.registry = registry;
    this.service = service;
    this.budget = budget;
    this.props = props;
    this.clock = clock;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    boolean ran = false;
    if (args.containsOption("rewrite-story")) {
      args.getOptionValues("rewrite-story").forEach(id -> rewriteStory(UUID.fromString(id)));
      ran = true;
    }
    if (args.containsOption("rewrite-source")) {
      args.getOptionValues("rewrite-source").forEach(this::rewriteSource);
      ran = true;
    }
    if (ran) {
      System.exit(SpringApplication.exit(context, () -> 0));
    }
  }

  public Outcome rewriteStory(UUID itemId) {
    Item item =
        items.find(itemId).orElseThrow(() -> new IllegalArgumentException("no item " + itemId));
    Outcome outcome = service.process(item.id(), input(item), gate());
    log.info("story rewritten item={} outcome={}", itemId, outcome);
    return outcome;
  }

  public Map<Outcome, Integer> rewriteSource(String sourceId) {
    Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
    for (UUID id : items.idsBySourceAndStatus(sourceId, StoryStatus.PUBLISHED)) {
      counts.merge(rewriteStory(id), 1, Integer::sum);
    }
    log.info("source rewritten source={} outcomes={}", sourceId, counts);
    return counts;
  }

  private StoryService.CallGate gate() {
    LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    return () -> budget.tryTake(today, props.dailyCallCap());
  }

  private StoryInput input(Item item) {
    var source = registry.find(item.sourceId());
    return new StoryInput(
        item.sourceTitle(),
        item.sourceExcerpt(),
        source.map(Source::name).orElse(item.sourceId()),
        source.map(s -> s.region().name()).orElse("GLOBAL"),
        source.flatMap(Source::hint).orElse(null),
        item.publishedAt());
  }
}
