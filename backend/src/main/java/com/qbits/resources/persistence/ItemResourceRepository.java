package com.qbits.resources.persistence;

import com.qbits.common.links.LinkOrigin;
import com.qbits.resources.domain.Candidate;
import com.qbits.resources.domain.ItemResource;
import com.qbits.resources.domain.ResourceHost;
import com.qbits.resources.domain.ResourceStatus;
import com.qbits.resources.domain.ResourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Candidate open-source links per item and their check state (spec 005). */
@Repository
public class ItemResourceRepository {

  private final JdbcClient jdbc;

  public ItemResourceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Stores a candidate once per item and URL. Returns true if it was new. */
  public boolean insertIfNew(UUID itemId, Candidate c, LinkOrigin origin, Instant now) {
    return jdbc.sql(
                """
                insert into item_resources (item_id, type, host, url, name, origin, next_check_at)
                values (:itemId, :type, :host, :url, :name, :origin, :now)
                on conflict (item_id, url) do nothing
                """)
            .param("itemId", itemId)
            .param("type", c.type().name())
            .param("host", c.host().name())
            .param("url", c.url())
            .param("name", c.name())
            .param("origin", origin.name())
            .param("now", Timestamp.from(now))
            .update()
        == 1;
  }

  /** Candidates waiting for a check, oldest first, for items published since {@code since}. */
  public List<ItemResource> findDue(Instant now, Instant since, int limit) {
    return jdbc.sql(
            """
            select r.* from item_resources r join items i on i.id = r.item_id
            where r.status in ('PENDING', 'CHECK_FAILED') and r.next_check_at <= :now
              and i.published_at >= :since
            order by r.next_check_at, r.id
            limit :limit
            """)
        .param("now", Timestamp.from(now))
        .param("since", Timestamp.from(since))
        .param("limit", limit)
        .query(ItemResourceRepository::map)
        .list();
  }

  /**
   * Marks a link verified under {@code url}. If the same item already holds that URL in another row
   * (for example after a GitHub rename, or two spellings of one repo), this row is removed and the
   * other row is kept and marked verified, so the unique (item, url) rule always holds.
   */
  @Transactional
  public void markVerified(long id, String url, String name, Instant at) {
    Optional<Long> existing =
        jdbc.sql(
                """
                select o.id from item_resources o join item_resources r on r.item_id = o.item_id
                where r.id = :id and o.url = :url and o.id <> :id
                """)
            .param("id", id)
            .param("url", url)
            .query(Long.class)
            .optional();
    if (existing.isPresent()) {
      jdbc.sql("delete from item_resources where id = :id").param("id", id).update();
    }
    long target = existing.orElse(id);
    jdbc.sql(
            """
            update item_resources set status = 'VERIFIED', url = :url, name = :name,
              checked_at = :at
            where id = :id
            """)
        .param("id", target)
        .param("url", url)
        .param("name", name)
        .param("at", Timestamp.from(at))
        .update();
  }

  public void markNotFound(long id, Instant at) {
    jdbc.sql("update item_resources set status = 'NOT_FOUND', checked_at = :at where id = :id")
        .param("id", id)
        .param("at", Timestamp.from(at))
        .update();
  }

  public void markFailed(long id, int attempts, Instant nextCheckAt, Instant at) {
    jdbc.sql(
            """
            update item_resources set status = 'CHECK_FAILED', attempts = :attempts,
              next_check_at = :next, checked_at = :at
            where id = :id
            """)
        .param("id", id)
        .param("attempts", attempts)
        .param("next", Timestamp.from(nextCheckAt))
        .param("at", Timestamp.from(at))
        .update();
  }

  public List<ItemResource> findByItem(UUID itemId) {
    return jdbc.sql("select * from item_resources where item_id = :id order by id")
        .param("id", itemId)
        .query(ItemResourceRepository::map)
        .list();
  }

  /** Verified resources for many items at once (one query per page, spec 005 T6). */
  public Map<UUID, List<ItemResource>> findVerified(Collection<UUID> itemIds) {
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    return jdbc
        .sql(
            "select * from item_resources where status = 'VERIFIED' and item_id = any(:ids) order by id")
        .param("ids", itemIds.toArray(UUID[]::new))
        .query(ItemResourceRepository::map)
        .list()
        .stream()
        .collect(Collectors.groupingBy(ItemResource::itemId));
  }

  static ItemResource map(ResultSet rs, int n) throws SQLException {
    Timestamp checked = rs.getTimestamp("checked_at");
    return new ItemResource(
        rs.getLong("id"),
        rs.getObject("item_id", UUID.class),
        ResourceType.valueOf(rs.getString("type")),
        ResourceHost.valueOf(rs.getString("host")),
        rs.getString("url"),
        rs.getString("name"),
        LinkOrigin.valueOf(rs.getString("origin")),
        ResourceStatus.valueOf(rs.getString("status")),
        rs.getInt("attempts"),
        rs.getTimestamp("next_check_at").toInstant(),
        checked == null ? null : checked.toInstant());
  }
}
