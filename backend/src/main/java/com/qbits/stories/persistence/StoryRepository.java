package com.qbits.stories.persistence;

import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import com.qbits.stories.domain.Story;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores written stories. Saving again replaces the story (operator rewrites, R10.2). */
@Repository
public class StoryRepository {

  private static final TypeReference<List<KeyTerm>> KEY_TERMS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public StoryRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void save(Story s) {
    jdbc.sql(
            """
            insert into stories (item_id, section, headline, summary, key_terms, model,
              prompt_version, written_at, input_tokens, output_tokens, attempts)
            values (:itemId, :section, :headline, :summary, cast(:keyTerms as jsonb), :model,
              :promptVersion, :writtenAt, :inputTokens, :outputTokens, :attempts)
            on conflict (item_id) do update set
              section = excluded.section, headline = excluded.headline,
              summary = excluded.summary, key_terms = excluded.key_terms,
              model = excluded.model, prompt_version = excluded.prompt_version,
              written_at = excluded.written_at, input_tokens = excluded.input_tokens,
              output_tokens = excluded.output_tokens, attempts = excluded.attempts
            """)
        .param("itemId", s.itemId())
        .param("section", s.section().name())
        .param("headline", s.headline())
        .param("summary", s.summary())
        .param("keyTerms", json.writeValueAsString(s.keyTerms()))
        .param("model", s.model())
        .param("promptVersion", s.promptVersion())
        .param("writtenAt", Timestamp.from(s.writtenAt()))
        .param("inputTokens", s.inputTokens())
        .param("outputTokens", s.outputTokens())
        .param("attempts", s.attempts())
        .update();
  }

  /** A story's own text and timing, for same-event grouping (spec 008 R1.3). */
  public record CandidateRow(
      UUID id,
      String sourceId,
      Instant publishedAt,
      boolean published,
      Section section,
      String headline,
      String summary) {}

  private static final String CANDIDATES =
      """
      select i.id, i.source_id, i.published_at, i.story_status, s.section, s.headline, s.summary
      from items i join stories s on s.item_id = i.id
      where not i.hidden and (%s)
      order by i.published_at, i.id
      """;

  /**
   * An edition's written stories plus the published stories since {@code windowStart}: what a new
   * edition is checked against (spec 008 R1.1).
   */
  public List<CandidateRow> editionCandidates(UUID editionId, Instant windowStart) {
    return jdbc.sql(
            CANDIDATES.formatted(
                "(i.edition_id = :e and i.story_status = 'WRITTEN')"
                    + " or (i.story_status = 'PUBLISHED' and i.published_at >= :since)"))
        .param("e", editionId)
        .param("since", Timestamp.from(windowStart))
        .query(StoryRepository::candidate)
        .list();
  }

  /** The published stories since {@code windowStart} (spec 008 R4.1). */
  public List<CandidateRow> publishedCandidates(Instant windowStart) {
    return jdbc.sql(
            CANDIDATES.formatted("i.story_status = 'PUBLISHED' and i.published_at >= :since"))
        .param("since", Timestamp.from(windowStart))
        .query(StoryRepository::candidate)
        .list();
  }

  private static CandidateRow candidate(ResultSet rs, int row) throws SQLException {
    return new CandidateRow(
        rs.getObject("id", UUID.class),
        rs.getString("source_id"),
        rs.getTimestamp("published_at").toInstant(),
        "PUBLISHED".equals(rs.getString("story_status")),
        Section.valueOf(rs.getString("section")),
        rs.getString("headline"),
        rs.getString("summary"));
  }

  public Optional<Story> find(UUID itemId) {
    return jdbc.sql("select * from stories where item_id = :id")
        .param("id", itemId)
        .query(
            (rs, i) ->
                new Story(
                    rs.getObject("item_id", UUID.class),
                    Section.valueOf(rs.getString("section")),
                    rs.getString("headline"),
                    rs.getString("summary"),
                    json.readValue(rs.getString("key_terms"), KEY_TERMS),
                    rs.getString("model"),
                    rs.getString("prompt_version"),
                    rs.getTimestamp("written_at").toInstant(),
                    rs.getInt("input_tokens"),
                    rs.getInt("output_tokens"),
                    rs.getInt("attempts")))
        .optional();
  }
}
