package com.qbits.ingestion.persistence;

import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stores item metadata. Inserts are idempotent on canonical URL (spec 001 R6). */
@Repository
public class ItemRepository {

  private final JdbcClient jdbc;

  public ItemRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Inserts the item unless its canonical URL is already stored. Returns true if inserted. */
  public boolean insertIfNew(Item item) {
    int rows =
        jdbc.sql(
                """
                insert into items (id, source_id, canonical_url, source_title, source_excerpt,
                  title_key, published_at, date_estimated, fetched_at, prefilter_score,
                  story_status, hidden)
                values (:id, :sourceId, :url, :title, :excerpt, :titleKey, :publishedAt,
                  :dateEstimated, :fetchedAt, :score, :status, :hidden)
                on conflict (canonical_url) do nothing
                """)
            .param("id", item.id())
            .param("sourceId", item.sourceId())
            .param("url", item.canonicalUrl())
            .param("title", item.sourceTitle())
            .param("excerpt", item.sourceExcerpt())
            .param("titleKey", item.titleKey())
            .param("publishedAt", Timestamp.from(item.publishedAt()))
            .param("dateEstimated", item.dateEstimated())
            .param("fetchedAt", Timestamp.from(item.fetchedAt()))
            .param("score", item.prefilterScore())
            .param("status", item.storyStatus().name())
            .param("hidden", item.hidden())
            .update();
    return rows == 1;
  }

  public boolean existsByCanonicalUrl(String canonicalUrl) {
    return jdbc.sql("select exists(select 1 from items where canonical_url = :url)")
        .param("url", canonicalUrl)
        .query(Boolean.class)
        .single();
  }

  /** True if the same source stored an item with the same title key since the given time. */
  public boolean existsSameTitle(String sourceId, String titleKey, Instant since) {
    return jdbc.sql(
            """
            select exists(select 1 from items
              where source_id = :sourceId and title_key = :titleKey and published_at >= :since)
            """)
        .param("sourceId", sourceId)
        .param("titleKey", titleKey)
        .param("since", Timestamp.from(since))
        .query(Boolean.class)
        .single();
  }

  public Optional<Item> findById(UUID id) {
    return jdbc.sql("select * from items where id = :id")
        .param("id", id)
        .query(ItemRepository::map)
        .optional();
  }

  /** Moves an item to a new story status with an optional note (spec 002). */
  public void updateStoryStatus(UUID id, StoryStatus status, String note) {
    String trimmed = note != null && note.length() > 300 ? note.substring(0, 300) : note;
    jdbc.sql("update items set story_status = :status, story_note = :note where id = :id")
        .param("status", status.name())
        .param("note", trimmed)
        .param("id", id)
        .update();
  }

  public java.util.Optional<String> findStoryNote(UUID id) {
    return jdbc.sql("select story_note from items where id = :id")
        .param("id", id)
        .query(String.class)
        .optional();
  }

  public long countBySource(String sourceId) {
    return jdbc.sql("select count(*) from items where source_id = :sourceId")
        .param("sourceId", sourceId)
        .query(Long.class)
        .single();
  }

  /**
   * Hides items of sources that are not enabled and shows items of enabled ones (spec 001 R8.1).
   * Returns the number of rows changed.
   */
  public int syncVisibility(java.util.Collection<String> enabledSourceIds) {
    String[] ids = enabledSourceIds.toArray(String[]::new);
    return jdbc.sql(
            """
            update items set hidden = not (source_id = any(:ids))
            where hidden is distinct from not (source_id = any(:ids))
            """)
        .param("ids", ids)
        .update();
  }

  /** Hides or shows every item of a source (spec 001 R8.1). */
  public int setHiddenForSource(String sourceId, boolean hidden) {
    return jdbc.sql(
            "update items set hidden = :hidden where source_id = :sourceId and hidden <> :hidden")
        .param("hidden", hidden)
        .param("sourceId", sourceId)
        .update();
  }

  /** Deletes every item of a source (spec 001 R8.2). */
  public int deleteBySource(String sourceId) {
    return jdbc.sql("delete from items where source_id = :sourceId")
        .param("sourceId", sourceId)
        .update();
  }

  /** Deletes items published before the cut-off (spec 001 R9.2). */
  public int deletePublishedBefore(Instant cutoff) {
    return jdbc.sql("delete from items where published_at < :cutoff")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
  }

  static Item map(ResultSet rs, int row) throws SQLException {
    return new Item(
        rs.getObject("id", UUID.class),
        rs.getString("source_id"),
        rs.getString("canonical_url"),
        rs.getString("source_title"),
        rs.getString("source_excerpt"),
        rs.getString("title_key"),
        rs.getTimestamp("published_at").toInstant(),
        rs.getBoolean("date_estimated"),
        rs.getTimestamp("fetched_at").toInstant(),
        rs.getDouble("prefilter_score"),
        StoryStatus.valueOf(rs.getString("story_status")),
        rs.getBoolean("hidden"));
  }
}
