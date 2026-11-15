package com.qbits.stories.persistence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Shared back-off for jobs that call an outside service. */
@Repository
public class JobBackoffRepository {

  /** Current back-off for a job. */
  public record Backoff(Instant nextAttemptAt, Duration delay) {}

  private final JdbcClient jdbc;

  public JobBackoffRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Backoff> find(String name) {
    return jdbc.sql("select next_attempt_at, backoff_seconds from job_backoff where name = :name")
        .param("name", name)
        .query(
            (rs, i) ->
                new Backoff(
                    rs.getTimestamp("next_attempt_at").toInstant(),
                    Duration.ofSeconds(rs.getInt("backoff_seconds"))))
        .optional();
  }

  public void save(String name, Backoff backoff) {
    jdbc.sql(
            """
            insert into job_backoff (name, next_attempt_at, backoff_seconds)
            values (:name, :next, :seconds)
            on conflict (name) do update set
              next_attempt_at = excluded.next_attempt_at, backoff_seconds = excluded.backoff_seconds
            """)
        .param("name", name)
        .param("next", Timestamp.from(backoff.nextAttemptAt()))
        .param("seconds", (int) backoff.delay().toSeconds())
        .update();
  }

  public void clear(String name) {
    jdbc.sql("delete from job_backoff where name = :name").param("name", name).update();
  }
}
