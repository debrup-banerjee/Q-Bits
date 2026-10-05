package com.qbits.ingestion.persistence;

import com.qbits.ingestion.domain.FetchLogEntry;
import com.qbits.ingestion.domain.FetchStatus;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only log of every fetch attempt (spec 001 R7.2). */
@Repository
public class FetchLogRepository {

  private static final int MAX_ERROR_LENGTH = 500;

  private final JdbcClient jdbc;

  public FetchLogRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(FetchLogEntry e) {
    String error = e.error();
    if (error != null && error.length() > MAX_ERROR_LENGTH) {
      error = error.substring(0, MAX_ERROR_LENGTH);
    }
    jdbc.sql(
            """
            insert into source_fetch_log (source_id, started_at, duration_ms, status, http_status,
              items_new, items_dup, items_not_ai, items_bad, items_too_old, error)
            values (:sourceId, :startedAt, :durationMs, :status, :httpStatus, :new, :dup, :notAi,
              :bad, :tooOld, :error)
            """)
        .param("sourceId", e.sourceId())
        .param("startedAt", Timestamp.from(e.startedAt()))
        .param("durationMs", e.durationMs())
        .param("status", e.status().name())
        .param("httpStatus", e.httpStatus())
        .param("new", e.itemsNew())
        .param("dup", e.itemsDuplicate())
        .param("notAi", e.itemsNotAi())
        .param("bad", e.itemsMalformed())
        .param("tooOld", e.itemsTooOld())
        .param("error", error)
        .update();
  }

  /** Newest first. */
  public List<FetchLogEntry> findBySource(String sourceId) {
    return jdbc.sql(
            "select * from source_fetch_log where source_id = :id order by started_at desc, id desc")
        .param("id", sourceId)
        .query(
            (rs, i) ->
                new FetchLogEntry(
                    rs.getString("source_id"),
                    rs.getTimestamp("started_at").toInstant(),
                    rs.getLong("duration_ms"),
                    FetchStatus.valueOf(rs.getString("status")),
                    (Integer) rs.getObject("http_status"),
                    rs.getInt("items_new"),
                    rs.getInt("items_dup"),
                    rs.getInt("items_not_ai"),
                    rs.getInt("items_bad"),
                    rs.getInt("items_too_old"),
                    rs.getString("error")))
        .list();
  }
}
