package com.qbits.images.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Feed image URLs of items from sources with a recorded image permission (spec 009 R3.2). */
@Repository
public class FeedImageRepository {

  private final JdbcClient jdbc;

  public FeedImageRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insertIfNew(UUID itemId, String url, Instant now) {
    jdbc.sql(
            """
            insert into item_feed_images (item_id, url, found_at) values (:id, :url, :now)
            on conflict (item_id) do nothing
            """)
        .param("id", itemId)
        .param("url", url)
        .param("now", Timestamp.from(now))
        .update();
  }
}
