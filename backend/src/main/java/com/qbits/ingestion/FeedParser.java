package com.qbits.ingestion;

import com.qbits.common.links.FoundLink;
import com.qbits.common.links.LinkOrigin;
import com.qbits.ingestion.domain.RawEntry;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jdom2.Element;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Reads RSS and Atom with Rome. Takes only title, link, dates and the description/summary, plus the
 * URLs of links found in the entry (spec 005) and of the image the feed attaches to it (spec 009).
 * Text from full content elements (such as {@code content:encoded}) is never kept: only link
 * targets are read from them (principles).
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

  /** The Media RSS namespace, used by most feeds that attach an image to an entry. */
  static final String MEDIA_RSS = "http://search.yahoo.com/mrss/";

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
        links(e, description),
        imageUrl(e));
  }

  /**
   * The image the feed attaches to the entry, if any (spec 009 R3.2): an image enclosure, else a
   * Media RSS {@code content} (medium or type image) or {@code thumbnail}, also inside a {@code
   * group}. Only an https URL is returned. Images inside description or content HTML are never
   * used: the publisher did not mark them as the entry's image.
   */
  static String imageUrl(SyndEntry e) {
    for (SyndEnclosure enc : e.getEnclosures()) {
      if (enc.getType() != null && enc.getType().toLowerCase(Locale.ROOT).startsWith("image/")) {
        String url = httpsUrl(enc.getUrl());
        if (url != null) {
          return url;
        }
      }
    }
    String thumbnail = null;
    for (Element el : media(e.getForeignMarkup())) {
      if ("content".equals(el.getName()) && isImage(el)) {
        String url = httpsUrl(el.getAttributeValue("url"));
        if (url != null) {
          return url;
        }
      } else if ("thumbnail".equals(el.getName()) && thumbnail == null) {
        thumbnail = httpsUrl(el.getAttributeValue("url"));
      }
    }
    return thumbnail;
  }

  /** Media RSS elements of the entry, with those inside {@code media:group} flattened in. */
  private static List<Element> media(List<Element> markup) {
    List<Element> out = new ArrayList<>();
    for (Element el : markup) {
      if (!MEDIA_RSS.equals(el.getNamespaceURI())) {
        continue;
      }
      if ("group".equals(el.getName())) {
        out.addAll(media(el.getChildren()));
      } else {
        out.add(el);
      }
    }
    return out;
  }

  private static boolean isImage(Element content) {
    String medium = content.getAttributeValue("medium");
    String type = content.getAttributeValue("type");
    return "image".equalsIgnoreCase(medium)
        || (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/"));
  }

  private static String httpsUrl(String url) {
    if (url == null) {
      return null;
    }
    String trimmed = url.trim();
    return trimmed.startsWith("https://") && trimmed.length() <= 600 ? trimmed : null;
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
