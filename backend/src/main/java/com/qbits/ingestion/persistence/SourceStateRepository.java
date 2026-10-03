package com.qbits.ingestion.persistence;

import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Per-source fetch memory: validators for conditional GET, timing and health. */
@Repository
public class SourceStateRepository {

  private final JdbcClient jdbc;

  public SourceStateRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<SourceState> find(String sourceId) {
    return jdbc.sql("select * from sources_state where source_id = :id")
        .param("id", sourceId)
        .query(
            (rs, i) ->
                new SourceState(
                    rs.getString("source_id"),
                    rs.getString("etag"),
                    rs.getString("last_modified"),
                    instant(rs.getTimestamp("last_fetched_at")),
                    instant(rs.getTimestamp("last_success_at")),
                    rs.getInt("consecutive_failures"),
                    SourceHealth.valueOf(rs.getString("health"))))
        .optional();
  }

  public List<SourceState> findAll() {
    return jdbc
        .sql("select source_id from sources_state order by source_id")
        .query(String.class)
        .list()
        .stream()
        .map(id -> find(id).orElseThrow())
        .toList();
  }

  public void save(SourceState state) {
    jdbc.sql(
            """
            insert into sources_state (source_id, etag, last_modified, last_fetched_at,
              last_success_at, consecutive_failures, health)
            values (:id, :etag, :lastModified, :lastFetchedAt, :lastSuccessAt, :failures, :health)
            on conflict (source_id) do update set
              etag = excluded.etag,
              last_modified = excluded.last_modified,
              last_fetched_at = excluded.last_fetched_at,
              last_success_at = excluded.last_success_at,
              consecutive_failures = excluded.consecutive_failures,
              health = excluded.health
            """)
        .param("id", state.sourceId())
        .param("etag", state.etag())
        .param("lastModified", state.lastModified())
        .param("lastFetchedAt", timestamp(state.lastFetchedAt()))
        .param("lastSuccessAt", timestamp(state.lastSuccessAt()))
        .param("failures", state.consecutiveFailures())
        .param("health", state.health().name())
        .update();
  }

  private static Instant instant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
