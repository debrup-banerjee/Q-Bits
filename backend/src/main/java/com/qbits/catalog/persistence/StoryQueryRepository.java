package com.qbits.catalog.persistence;

import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-side queries for published stories inside the 72-hour window (spec 003 R2). Only stories
 * that are PUBLISHED, not hidden and new enough are ever returned.
 */
@Repository
public class StoryQueryRepository {

  /** A published story joined with its item's link and timing. */
  public record Row(
      UUID id,
      String sourceId,
      String canonicalUrl,
      Instant publishedAt,
      boolean dateEstimated,
      Section section,
      String headline,
      String summary,
      List<KeyTerm> keyTerms) {}

  private static final TypeReference<List<KeyTerm>> KEY_TERMS = new TypeReference<>() {};

  private static final String SELECT =
      """
      select i.id, i.source_id, i.canonical_url, i.published_at, i.date_estimated,
             s.section, s.headline, s.summary, s.key_terms
      from items i join stories s on s.item_id = i.id
      where i.story_status = 'PUBLISHED' and not i.hidden and i.published_at >= :windowStart
      """;

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public StoryQueryRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Newest first; returns up to {@code limit} rows strictly after the cursor position. */
  public List<Row> list(
      Instant windowStart,
      Optional<Section> section,
      Optional<Instant> afterAt,
      Optional<UUID> afterId,
      int limit) {
    String sql =
        SELECT
            + (section.isPresent() ? " and s.section = :section" : "")
            + (afterAt.isPresent() ? " and (i.published_at, i.id) < (:afterAt, :afterId)" : "")
            + " order by i.published_at desc, i.id desc limit :limit";
    var spec =
        jdbc.sql(sql).param("windowStart", Timestamp.from(windowStart)).param("limit", limit);
    if (section.isPresent()) {
      spec = spec.param("section", section.get().name());
    }
    if (afterAt.isPresent()) {
      spec =
          spec.param("afterAt", Timestamp.from(afterAt.get()))
              .param("afterId", afterId.orElseThrow());
    }
    return spec.query(this::map).list();
  }

  public Optional<Row> find(UUID id, Instant windowStart) {
    return jdbc.sql(SELECT + " and i.id = :id")
        .param("windowStart", Timestamp.from(windowStart))
        .param("id", id)
        .query(this::map)
        .optional();
  }

  /** Stories per section inside the window; every section is present. */
  public Map<Section, Long> countBySection(Instant windowStart) {
    Map<Section, Long> counts = new EnumMap<>(Section.class);
    for (Section s : Section.values()) {
      counts.put(s, 0L);
    }
    jdbc.sql(
            """
            select s.section, count(*) as n
            from items i join stories s on s.item_id = i.id
            where i.story_status = 'PUBLISHED' and not i.hidden and i.published_at >= :windowStart
            group by s.section
            """)
        .param("windowStart", Timestamp.from(windowStart))
        .query((rs, n) -> Map.entry(Section.valueOf(rs.getString("section")), rs.getLong("n")))
        .list()
        .forEach(e -> counts.put(e.getKey(), e.getValue()));
    return counts;
  }

  /** Publish time of the newest visible story, for the "updated" line (R2.7). */
  public Optional<Instant> latestPublishedAt(Instant windowStart) {
    return jdbc.sql(
            """
            select max(i.published_at) from items i join stories s on s.item_id = i.id
            where i.story_status = 'PUBLISHED' and not i.hidden and i.published_at >= :windowStart
            """)
        .param("windowStart", Timestamp.from(windowStart))
        .query((rs, n) -> rs.getTimestamp(1))
        .optional()
        .filter(ts -> ts != null)
        .map(Timestamp::toInstant);
  }

  private Row map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getString("source_id"),
        rs.getString("canonical_url"),
        rs.getTimestamp("published_at").toInstant(),
        rs.getBoolean("date_estimated"),
        Section.valueOf(rs.getString("section")),
        rs.getString("headline"),
        rs.getString("summary"),
        json.readValue(rs.getString("key_terms"), KEY_TERMS));
  }
}
