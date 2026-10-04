package com.qbits.ingestion.persistence;

import com.qbits.ingestion.domain.Item;
import com.qbits.ingestion.domain.StoryStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stores item metadata. Inserts are idempotent on canonical URL (spec 001 R6). */
@Repository
public class ItemRepository {

  /** Column limits from the schema (V2, V5). */
  static final int MAX_NOTE_CHARS = 300;

  static final int MAX_FEEDBACK_CHARS = 1000;

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

  /**
   * Moves an item to a new story status with an optional note (spec 002). Retry feedback is kept
   * only while the item is PENDING.
   */
  public void updateStoryStatus(UUID id, StoryStatus status, String note) {
    jdbc.sql(
            """
            update items set story_status = :status, story_note = :note,
              retry_feedback = case when :status = 'PENDING' then retry_feedback end
            where id = :id
            """)
        .param("status", status.name())
        .param("note", fit(note, MAX_NOTE_CHARS))
        .param("id", id)
        .update();
  }

  public Optional<String> findStoryNote(UUID id) {
    return jdbc.sql("select story_note from items where id = :id")
        .param("id", id)
        .query(String.class)
        .optional();
  }

  /** Visible PENDING items published at or after {@code since}, newest first (spec 002 R1.1). */
  public List<Item> findPendingNewestFirst(Instant since, int limit) {
    return jdbc.sql(
            """
            select * from items
            where story_status = 'PENDING' and not hidden and published_at >= :since
            order by published_at desc, id desc
            limit :limit
            """)
        .param("since", Timestamp.from(since))
        .param("limit", limit)
        .query(ItemRepository::map)
        .list();
  }

  /** Marks PENDING items older than the cut-off as EXPIRED (spec 002 R9.3). */
  public int expirePendingBefore(Instant cutoff) {
    return jdbc.sql(
            """
            update items set story_status = 'EXPIRED', retry_feedback = null
            where story_status = 'PENDING' and published_at < :cutoff
            """)
        .param("cutoff", Timestamp.from(cutoff))
        .update();
  }

  public List<UUID> findIdsBySourceAndStatus(String sourceId, StoryStatus status) {
    return jdbc.sql("select id from items where source_id = :sourceId and story_status = :status")
        .param("sourceId", sourceId)
        .param("status", status.name())
        .query(UUID.class)
        .list();
  }

  /**
   * Puts up to {@code limit} waiting items into an edition, newest first: PENDING, visible, not in
   * another edition, fetched by the cut-off and published within the window (spec 006 R1.2, R1.3).
   * Safe when two cuts run at once: rows another cut has locked are skipped, and the outer check
   * never moves an item that already has an edition.
   */
  public int assignToEdition(UUID editionId, Instant cutoff, Instant since, int limit) {
    return jdbc.sql(
            """
            with picked as materialized (
              select id from items
              where story_status = 'PENDING' and not hidden and edition_id is null
                and fetched_at <= :cutoff and published_at >= :since
              order by published_at desc, id desc
              limit :limit
              for update skip locked)
            update items set edition_id = :edition
            from picked
            where items.id = picked.id
              and items.edition_id is null and items.story_status = 'PENDING'
            """)
        .param("edition", editionId)
        .param("cutoff", Timestamp.from(cutoff))
        .param("since", Timestamp.from(since))
        .param("limit", limit)
        .update();
  }

  /** Items of an edition that still wait for a story, oldest first. */
  public List<Item> findPendingInEdition(UUID editionId) {
    return jdbc.sql(
            "select * from items where edition_id = :e and story_status = 'PENDING' order by published_at, id")
        .param("e", editionId)
        .query(ItemRepository::map)
        .list();
  }

  public void setRetryFeedback(UUID id, String feedback) {
    jdbc.sql("update items set retry_feedback = :f where id = :id")
        .param("f", fit(feedback, MAX_FEEDBACK_CHARS))
        .param("id", id)
        .update();
  }

  public Optional<String> findRetryFeedback(UUID id) {
    return jdbc.sql("select retry_feedback from items where id = :id")
        .param("id", id)
        .query(String.class)
        .optional();
  }

  /** Sends one item back to wait for the next edition (spec 006 R6.2). */
  public void releaseFromEdition(UUID id) {
    jdbc.sql(
            "update items set edition_id = null, retry_feedback = null where id = :id and story_status = 'PENDING'")
        .param("id", id)
        .update();
  }

  /** Publishes every WRITTEN story of an edition at once (spec 006 R3.1). Returns the count. */
  public int publishEdition(UUID editionId) {
    return jdbc.sql(
            "update items set story_status = 'PUBLISHED' where edition_id = :e and story_status = 'WRITTEN'")
        .param("e", editionId)
        .update();
  }

  /** Items of an edition still PENDING go back for the next edition. */
  public int releaseUnfinished(UUID editionId) {
    return jdbc.sql(
            """
            update items set edition_id = null, retry_feedback = null
            where edition_id = :e and story_status = 'PENDING'
            """)
        .param("e", editionId)
        .update();
  }

  /** How many of an edition's items are in each story status (spec 006 R1.4). */
  public Map<StoryStatus, Integer> countByStatusInEdition(UUID editionId) {
    Map<StoryStatus, Integer> counts = new EnumMap<>(StoryStatus.class);
    jdbc.sql(
            "select story_status, count(*) n from items where edition_id = :e group by story_status")
        .param("e", editionId)
        .query(
            rs -> {
              counts.put(StoryStatus.valueOf(rs.getString("story_status")), rs.getInt("n"));
            });
    return Map.copyOf(counts);
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
  public int syncVisibility(Collection<String> enabledSourceIds) {
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

  /**
   * Cuts text to the column's limit at a character boundary, counting characters the way PostgreSQL
   * does, and drops NUL characters, which PostgreSQL cannot store.
   */
  static String fit(String text, int maxChars) {
    if (text == null) {
      return null;
    }
    String clean = text.replace("\u0000", "");
    if (clean.codePointCount(0, clean.length()) <= maxChars) {
      return clean;
    }
    return clean.substring(0, clean.offsetByCodePoints(0, maxChars));
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
