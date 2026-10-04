package com.qbits.catalog;

import com.qbits.catalog.domain.Cursor;
import com.qbits.catalog.domain.CursorCodec;
import com.qbits.catalog.domain.StoryPage;
import com.qbits.catalog.domain.StoryView;
import com.qbits.catalog.persistence.StoryQueryRepository;
import com.qbits.catalog.persistence.StoryQueryRepository.Row;
import com.qbits.resources.ResourceQueries;
import com.qbits.resources.domain.ResourceLink;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import com.qbits.stories.digest.EditionQueries;
import com.qbits.stories.domain.Section;
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
  private final ResourceQueries resources;
  private final EditionQueries editions;

  public CatalogService(
      StoryQueryRepository queries,
      SourceRegistry registry,
      ResourceQueries resources,
      EditionQueries editions) {
    this.queries = queries;
    this.registry = registry;
    this.resources = resources;
    this.editions = editions;
  }

  public StoryPage list(Optional<Section> section, Optional<Cursor> after, int limit) {
    return list(section, after, limit, WINDOW);
  }

  /** Stories inside the given window (at most 72 hours), newest first (spec 004 R1.1). */
  public StoryPage list(
      Optional<Section> section, Optional<Cursor> after, int limit, Duration window) {
    Duration capped = window.compareTo(WINDOW) > 0 ? WINDOW : window;
    List<Row> rows =
        queries.list(
            editions.windowEnd().minus(capped), // 006 R4.2
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
    Map<UUID, List<ResourceLink>> links =
        resources.verifiedFor(page.stream().map(Row::id).toList());
    return new StoryPage(
        page.stream().map(r -> view(r, links.getOrDefault(r.id(), List.of()))).toList(), next);
  }

  public Optional<StoryView> find(UUID id) {
    return queries
        .find(id, windowStart())
        .map(r -> view(r, resources.verifiedFor(List.of(r.id())).getOrDefault(r.id(), List.of())));
  }

  public Map<Section, Long> counts() {
    return queries.countBySection(windowStart());
  }

  /**
   * The latest edition's publish time, or the newest story's time before any edition and in
   * realtime mode (006 R5.2, R6.4).
   */
  public Optional<Instant> dataAsOf() {
    Optional<Instant> edition = editions.dataAsOf();
    return edition.isPresent() ? edition : queries.latestPublishedAt(windowStart());
  }

  public List<Source> enabledSources() {
    return registry.enabled();
  }

  private Instant windowStart() {
    return editions.windowEnd().minus(WINDOW);
  }

  private StoryView view(Row r, List<ResourceLink> links) {
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
        StoryView.attributionFor(name),
        links);
  }
}
