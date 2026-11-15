package com.qbits.ingestion.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Cached robots.txt responses per host, shared by all app instances (spec 001 R3.1). */
@Repository
public class RobotsCacheRepository {

  /** A cached robots.txt response. */
  public record CachedRobots(String host, String body, int statusCode, Instant fetchedAt) {}

  private final JdbcClient jdbc;

  public RobotsCacheRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<CachedRobots> find(String host) {
    return jdbc.sql("select * from robots_cache where host = :host")
        .param("host", host)
        .query(
            (rs, i) ->
                new CachedRobots(
                    rs.getString("host"),
                    rs.getString("body"),
                    rs.getInt("status_code"),
                    rs.getTimestamp("fetched_at").toInstant()))
        .optional();
  }

  public void save(CachedRobots robots) {
    jdbc.sql(
            """
            insert into robots_cache (host, body, status_code, fetched_at)
            values (:host, :body, :status, :fetchedAt)
            on conflict (host) do update set
              body = excluded.body, status_code = excluded.status_code,
              fetched_at = excluded.fetched_at
            """)
        .param("host", robots.host())
        .param("body", robots.body())
        .param("status", robots.statusCode())
        .param("fetchedAt", Timestamp.from(robots.fetchedAt()))
        .update();
  }
}
