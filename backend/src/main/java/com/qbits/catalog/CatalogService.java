package com.qbits.catalog;

import com.qbits.catalog.domain.Cursor;
import com.qbits.catalog.domain.CursorCodec;
import com.qbits.catalog.domain.StoryPage;
import com.qbits.catalog.domain.StoryView;
import com.qbits.catalog.persistence.StoryQueryRepository;
import com.qbits.catalog.persistence.StoryQueryRepository.Row;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import com.qbits.stories.domain.Section;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Read model for clients: the last 72 hours of published stories (spec 003). */
@Service
public class CatalogService {

  public static final Duration WINDOW = Duration.ofHours(72);

  private final StoryQueryRepository queries;
  private final SourceRegistry registry;
  private final Clock clock;

  public CatalogService(StoryQueryRepository queries, SourceRegistry registry, Clock clock) {
    this.queries = queries;
    this.registry = registry;
    this.clock = clock;
  }

  public StoryPage list(Optional<Section> section, Optional<Cursor> after, int limit) {
    List<Row> rows =
        queries.list(
            windowStart(),
            section,
            after.map(Cursor::publishedAt),
            after.map(Cursor::id),
            limit + 1);
    boolean more = rows.size() > limit;
    List<Row> page = more ? rows.subList(0, limit) : rows;
    String next =
        more
            ? CursorCodec.encode(new Cursor(page.getLast().publishedAt(), page.getLast().id()))
            : null;
    return new StoryPage(page.stream().map(this::view).toList(), next);
  }

  public Optional<StoryView> find(UUID id) {
    return queries.find(id, windowStart()).map(this::view);
  }

  public Map<Section, Long> counts() {
    return queries.countBySection(windowStart());
  }

  public Optional<Instant> dataAsOf() {
    return queries.latestPublishedAt(windowStart());
  }

  public List<Source> enabledSources() {
    return registry.enabled();
  }

  private Instant windowStart() {
    return clock.instant().minus(WINDOW);
  }

  private StoryView view(Row r) {
    Optional<Source> source = registry.find(r.sourceId());
    String name = source.map(Source::name).orElse(r.sourceId());
    String homepage = source.map(s -> s.homepage().toString()).orElse(null);
    return new StoryView(
        r.id(),
        new StoryView.SectionRef(r.section().slug(), r.section().displayName()),
        r.headline(),
        r.summary(),
        r.keyTerms(),
        new StoryView.SourceRef(name, homepage),
        r.canonicalUrl(),
        r.publishedAt(),
        r.dateEstimated(),
        StoryView.attributionFor(name));
  }
}
