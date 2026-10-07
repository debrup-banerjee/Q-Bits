package com.qbits.images.persistence;

import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The picture chosen for each story (spec 009 R4). */
@Repository
public class StoryImageRepository {

  private static final TypeReference<List<KeyTerm>> KEY_TERMS = new TypeReference<>() {};

  /** A written story that still needs a picture, with what the job may use to find one. */
  public record Due(
      UUID itemId,
      String sourceId,
      Section section,
      String headline,
      String summary,
      List<KeyTerm> keyTerms,
      String feedImageUrl,
      int attempts) {}

  /** A stored FOUND picture. */
  public record Found(
      UUID itemId,
      String kind,
      String provider,
      String url,
      String pageUrl,
      String alt,
      String credit,
      String creditUrl,
      String color) {}

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public StoryImageRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /**
   * Written or published stories inside the window with no picture yet, or whose retry is due.
   * Newest first, so today's edition is served before older stories.
   */
  public List<Due> findDue(Instant windowStart, Instant now, int limit) {
    return jdbc.sql(
            """
            select i.id, i.source_id, s.section, s.headline, s.summary, s.key_terms,
                   f.url as feed_image_url, coalesce(g.attempts, 0) as attempts
            from items i
            join stories s on s.item_id = i.id
            left join item_feed_images f on f.item_id = i.id
            left join story_images g on g.item_id = i.id
            where i.story_status in ('WRITTEN', 'PUBLISHED') and not i.hidden
              and i.published_at >= :windowStart
              and (g.item_id is null or (g.status = 'RETRY' and g.next_try_at <= :now))
            order by i.published_at desc, i.id desc
            limit :limit
            """)
        .param("windowStart", Timestamp.from(windowStart))
        .param("now", Timestamp.from(now))
        .param("limit", limit)
        .query(this::due)
        .list();
  }

  public void saveFound(
      UUID itemId,
      String kind,
      String provider,
      String providerId,
      String url,
      String pageUrl,
      String alt,
      String credit,
      String creditUrl,
      String color,
      String query,
      Instant now) {
    jdbc.sql(
            """
            insert into story_images (item_id, status, kind, provider, provider_id, url, page_url,
              alt, credit, credit_url, color, query, attempts, next_try_at, chosen_at)
            values (:id, 'FOUND', :kind, :provider, :providerId, :url, :pageUrl, :alt, :credit,
              :creditUrl, :color, :query, 0, null, :now)
            on conflict (item_id) do update set
              status = 'FOUND', kind = excluded.kind, provider = excluded.provider,
              provider_id = excluded.provider_id, url = excluded.url,
              page_url = excluded.page_url, alt = excluded.alt, credit = excluded.credit,
              credit_url = excluded.credit_url, color = excluded.color, query = excluded.query,
              next_try_at = null, chosen_at = excluded.chosen_at
            """)
        .param("id", itemId)
        .param("kind", kind)
        .param("provider", provider)
        .param("providerId", providerId)
        .param("url", url)
        .param("pageUrl", pageUrl)
        .param("alt", alt)
        .param("credit", credit)
        .param("creditUrl", creditUrl)
        .param("color", color)
        .param("query", query)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** No safe picture: clients draw the cover art. */
  public void saveNone(UUID itemId, String query, Instant now) {
    upsertStatus(itemId, "NONE", query, null, null, now);
  }

  public void saveRetry(UUID itemId, int attempts, Instant nextTryAt, String query, Instant now) {
    upsertStatus(itemId, "RETRY", query, attempts, nextTryAt, now);
  }

  private void upsertStatus(
      UUID itemId, String status, String query, Integer attempts, Instant nextTryAt, Instant now) {
    jdbc.sql(
            """
            insert into story_images (item_id, status, query, attempts, next_try_at, chosen_at)
            values (:id, :status, :query, coalesce(cast(:attempts as int), 0),
              cast(:next as timestamptz), :now)
            on conflict (item_id) do update set
              status = excluded.status, kind = null, provider = null, provider_id = null,
              url = null, page_url = null, alt = null, credit = null, credit_url = null,
              color = null, query = excluded.query,
              attempts = coalesce(cast(:attempts as int), story_images.attempts),
              next_try_at = excluded.next_try_at, chosen_at = excluded.chosen_at
            """)
        .param("id", itemId)
        .param("status", status)
        .param("query", query)
        .param("attempts", attempts)
        .param("next", nextTryAt == null ? null : Timestamp.from(nextTryAt))
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Photo ids from this provider chosen since {@code since}, so the feed does not repeat. */
  public Set<String> recentProviderIds(String provider, Instant since) {
    return new HashSet<>(
        jdbc.sql(
                """
                select provider_id from story_images
                where provider = :provider and provider_id is not null and chosen_at >= :since
                """)
            .param("provider", provider)
            .param("since", Timestamp.from(since))
            .query(String.class)
            .list());
  }

  public Map<UUID, Found> findFound(Collection<UUID> itemIds) {
    Map<UUID, Found> out = new HashMap<>();
    if (itemIds.isEmpty()) {
      return out;
    }
    jdbc.sql(
            """
            select item_id, kind, provider, url, page_url, alt, credit, credit_url, color
            from story_images where status = 'FOUND' and item_id in (:ids)
            """)
        .param("ids", itemIds)
        .query(
            (rs, n) ->
                new Found(
                    rs.getObject("item_id", UUID.class),
                    rs.getString("kind"),
                    rs.getString("provider"),
                    rs.getString("url"),
                    rs.getString("page_url"),
                    rs.getString("alt"),
                    rs.getString("credit"),
                    rs.getString("credit_url"),
                    rs.getString("color")))
        .list()
        .forEach(f -> out.put(f.itemId(), f));
    return out;
  }

  private Due due(ResultSet rs, int n) throws SQLException {
    return new Due(
        rs.getObject("id", UUID.class),
        rs.getString("source_id"),
        Section.valueOf(rs.getString("section")),
        rs.getString("headline"),
        rs.getString("summary"),
        json.readValue(rs.getString("key_terms"), KEY_TERMS),
        rs.getString("feed_image_url"),
        rs.getInt("attempts"));
  }
}
