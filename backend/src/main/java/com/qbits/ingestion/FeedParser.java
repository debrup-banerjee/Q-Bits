package com.qbits.ingestion;

import com.qbits.common.links.FoundLink;
import com.qbits.common.links.LinkOrigin;
import com.qbits.ingestion.domain.RawEntry;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Reads RSS and Atom with Rome. Takes only title, link, dates and the description/summary, plus the
 * URLs of links found in the entry (spec 005). Text from full content elements (such as {@code
 * content:encoded}) is never kept: only link targets are read from them (principles).
 */
@Component
public class FeedParser {

  /**
   * Upper bound on URLs collected from one entry (spec 005 R1.1). R1.1 asks for every link in the
   * entry; this cap is a safety limit so a feed with very long content cannot make one entry carry
   * thousands of URLs into ingestion. Links are taken in priority order (entry links, description,
   * then content), so the links R3.1 always keeps are collected first. Recorded in the 005 design
   * (approved changes, 2026-10-04).
   */
  static final int MAX_LINKS = 50;

  private static final Pattern PLAIN_URL = Pattern.compile("https?://[^\\s<>\"')\\]]+");

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
        instant(e.getUpdatedDate()),
        links(e, description));
  }

  /**
   * URLs in the entry, in priority order: its own links, then the description (anchors and plain
   * URLs), then the content element (anchors only). Content text is parsed and dropped here; only
   * URLs leave this method (spec 005 R1.2).
   */
  static List<FoundLink> links(SyndEntry e, String descriptionHtml) {
    Map<String, FoundLink> found = new LinkedHashMap<>();
    if (e.getLink() != null) {
      add(found, e.getLink(), LinkOrigin.ENTRY_LINK);
    }
    for (SyndLink l : e.getLinks()) {
      add(found, l.getHref(), LinkOrigin.ENTRY_LINK);
    }
    if (descriptionHtml != null && !descriptionHtml.isBlank()) {
      Document doc = Jsoup.parseBodyFragment(descriptionHtml);
      doc.select("a[href]").forEach(a -> add(found, a.attr("href"), LinkOrigin.DESCRIPTION));
      Matcher m = PLAIN_URL.matcher(doc.text());
      while (m.find()) {
        add(found, m.group(), LinkOrigin.DESCRIPTION);
      }
    }
    for (SyndContent c : e.getContents()) {
      if (c.getValue() != null) {
        Jsoup.parseBodyFragment(c.getValue())
            .select("a[href]")
            .forEach(a -> add(found, a.attr("href"), LinkOrigin.CONTENT));
      }
    }
    return List.copyOf(found.values());
  }

  private static void add(Map<String, FoundLink> found, String url, LinkOrigin origin) {
    if (url == null || found.size() >= MAX_LINKS) {
      return;
    }
    String trimmed = url.trim().replaceAll("[.,;:]+$", "");
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      found.putIfAbsent(trimmed, new FoundLink(trimmed, origin));
    }
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
