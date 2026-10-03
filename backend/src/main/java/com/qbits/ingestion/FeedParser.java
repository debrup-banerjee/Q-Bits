package com.qbits.ingestion;

import com.qbits.ingestion.domain.RawEntry;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads RSS and Atom with Rome. Takes only title, link, dates and the description/summary. Full
 * content elements (such as {@code content:encoded}) are deliberately ignored (principles: never
 * store article bodies).
 */
@Component
public class FeedParser {

  /** The feed could not be parsed. */
  public static class FeedParseException extends RuntimeException {
    public FeedParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public List<RawEntry> parse(byte[] body) {
    SyndFeed feed;
    try {
      SyndFeedInput input = new SyndFeedInput();
      input.setAllowDoctypes(false);
      feed = input.build(new XmlReader(new ByteArrayInputStream(body)));
    } catch (Exception e) {
      throw new FeedParseException("not a readable RSS/Atom feed: " + e.getMessage(), e);
    }
    return feed.getEntries().stream().map(FeedParser::toRaw).toList();
  }

  private static RawEntry toRaw(SyndEntry e) {
    String description = e.getDescription() == null ? null : e.getDescription().getValue();
    return new RawEntry(
        e.getTitle(),
        link(e),
        description,
        instant(e.getPublishedDate()),
        instant(e.getUpdatedDate()));
  }

  private static String link(SyndEntry e) {
    if (e.getLink() != null && !e.getLink().isBlank()) {
      return e.getLink();
    }
    return e.getLinks().stream()
        .filter(l -> l.getRel() == null || "alternate".equals(l.getRel()))
        .map(SyndLink::getHref)
        .findFirst()
        .orElse(null);
  }

  private static Instant instant(Date date) {
    return date == null ? null : date.toInstant();
  }
}
