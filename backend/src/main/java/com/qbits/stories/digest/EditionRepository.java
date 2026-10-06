package com.qbits.stories.digest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Editions table (spec 006). */
@Repository
public class EditionRepository {

  private final JdbcClient jdbc;

  public EditionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void create(Edition e) {
    jdbc.sql(
            """
            insert into editions (id, cutoff_at, status, created_at, next_submit_at)
            values (:id, :cutoff, :status, :created, :nextSubmit)
            """)
        .param("id", e.id())
        .param("cutoff", ts(e.cutoffAt()))
        .param("status", e.status().name())
        .param("created", ts(e.createdAt()))
        .param("nextSubmit", ts(e.nextSubmitAt()))
        .update();
  }

  public Optional<Edition> find(UUID id) {
    return jdbc.sql("select * from editions where id = :id")
        .param("id", id)
        .query(EditionRepository::map)
        .optional();
  }

  public Optional<Edition> findByCutoff(Instant cutoff) {
    return jdbc.sql("select * from editions where cutoff_at = :c")
        .param("c", ts(cutoff))
        .query(EditionRepository::map)
        .optional();
  }

  /** True when an edition was cut at or after {@code cutoff} (on schedule, late or on demand). */
  public boolean existsWithCutoffAtOrAfter(Instant cutoff) {
    return jdbc.sql("select exists (select 1 from editions where cutoff_at >= :c)")
        .param("c", ts(cutoff))
        .query(Boolean.class)
        .single();
  }

  /** The most recent edition whatever its status (spec 006 R6.5). */
  public Optional<Edition> findLatest() {
    return jdbc.sql("select * from editions order by cutoff_at desc limit 1")
        .query(EditionRepository::map)
        .optional();
  }

  /** The edition still being worked on, if any (oldest first). */
  public Optional<Edition> findOpen() {
    return jdbc.sql(
            """
            select * from editions where status in ('COLLECTING', 'SUBMITTED', 'RETRYING')
            order by cutoff_at limit 1
            """)
        .query(EditionRepository::map)
        .optional();
  }

  /** The most recently published edition (spec 006 R4.1). */
  public Optional<Edition> findLatestPublished() {
    return jdbc.sql(
            "select * from editions where status = 'PUBLISHED' order by cutoff_at desc limit 1")
        .query(EditionRepository::map)
        .optional();
  }

  public void updateStatus(UUID id, EditionStatus status) {
    jdbc.sql("update editions set status = :s where id = :id")
        .param("s", status.name())
        .param("id", id)
        .update();
  }

  public void markSubmitted(UUID id, String batchId, int submitted, Instant at) {
    jdbc.sql(
            """
            update editions set status = 'SUBMITTED', batch_id = :b, submitted = :n,
              last_polled_at = :at, next_submit_at = null where id = :id
            """)
        .param("b", batchId)
        .param("n", submitted)
        .param("at", ts(at))
        .param("id", id)
        .update();
  }

  public void markRetrying(UUID id, String retryBatchId, Instant at) {
    jdbc.sql(
            """
            update editions set status = 'RETRYING', retry_batch_id = :b, last_polled_at = :at,
              next_submit_at = null
            where id = :id
            """)
        .param("b", retryBatchId)
        .param("at", ts(at))
        .param("id", id)
        .update();
  }

  /** The retry round could not be submitted: stay in the round and try again later (R6.1). */
  public void markRetrySubmitFailed(UUID id, Instant nextSubmitAt) {
    jdbc.sql("update editions set status = 'RETRYING', next_submit_at = :n where id = :id")
        .param("n", ts(nextSubmitAt))
        .param("id", id)
        .update();
  }

  public void markSubmitFailed(UUID id, Instant nextSubmitAt) {
    jdbc.sql("update editions set next_submit_at = :n where id = :id")
        .param("n", ts(nextSubmitAt))
        .param("id", id)
        .update();
  }

  public void markPolled(UUID id, Instant at) {
    jdbc.sql("update editions set last_polled_at = :at where id = :id")
        .param("at", ts(at))
        .param("id", id)
        .update();
  }

  /** Stamps the edition as published with its final counts (spec 006 R1.4, R3.1). */
  public void markPublished(UUID id, int published, int notAi, int rejected, Instant at) {
    jdbc.sql(
            """
            update editions set status = 'PUBLISHED', published = :p, not_ai = :a, rejected = :r,
              published_at = :at
            where id = :id
            """)
        .param("p", published)
        .param("a", notAi)
        .param("r", rejected)
        .param("at", ts(at))
        .param("id", id)
        .update();
  }

  /** How many stories the edition held back as repeats of another (spec 008 R2.5). */
  public void setDuplicates(UUID id, int duplicates) {
    jdbc.sql("update editions set duplicates = :d where id = :id")
        .param("d", duplicates)
        .param("id", id)
        .update();
  }

  /** One more failed grouping attempt before publishing (spec 008 R3.1). */
  public void incrementDedupeAttempts(UUID id) {
    jdbc.sql("update editions set dedupe_attempts = dedupe_attempts + 1 where id = :id")
        .param("id", id)
        .update();
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  private static Instant in(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  static Edition map(ResultSet rs, int n) throws SQLException {
    return new Edition(
        rs.getObject("id", UUID.class),
        rs.getTimestamp("cutoff_at").toInstant(),
        EditionStatus.valueOf(rs.getString("status")),
        rs.getString("batch_id"),
        rs.getString("retry_batch_id"),
        rs.getInt("submitted"),
        rs.getInt("published"),
        rs.getInt("not_ai"),
        rs.getInt("rejected"),
        rs.getTimestamp("created_at").toInstant(),
        in(rs.getTimestamp("last_polled_at")),
        in(rs.getTimestamp("next_submit_at")),
        in(rs.getTimestamp("published_at")));
  }
}
