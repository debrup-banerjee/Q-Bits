package com.qbits.resources.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Results of host checks by URL, so a repo linked from many stories is checked once a day. */
@Repository
public class LinkCheckCacheRepository {

  /** A cached check. {@code canonical} is the GitHub owner/repo after renames, if any. */
  public record CachedCheck(String url, boolean found, String canonical, Instant checkedAt) {}

  private final JdbcClient jdbc;

  public LinkCheckCacheRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<CachedCheck> findFresh(String url, Instant notBefore) {
    return jdbc.sql("select * from link_check_cache where url = :url and checked_at >= :since")
        .param("url", url)
        .param("since", Timestamp.from(notBefore))
        .query(
            (rs, n) ->
                new CachedCheck(
                    rs.getString("url"),
                    "VERIFIED".equals(rs.getString("status")),
                    rs.getString("canonical"),
                    rs.getTimestamp("checked_at").toInstant()))
        .optional();
  }

  public void save(CachedCheck check) {
    jdbc.sql(
            """
            insert into link_check_cache (url, status, canonical, checked_at)
            values (:url, :status, :canonical, :at)
            on conflict (url) do update set status = excluded.status,
              canonical = excluded.canonical, checked_at = excluded.checked_at
            """)
        .param("url", check.url())
        .param("status", check.found() ? "VERIFIED" : "NOT_FOUND")
        .param("canonical", check.canonical())
        .param("at", Timestamp.from(check.checkedAt()))
        .update();
  }
}
