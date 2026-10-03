package com.qbits.ingestion.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;

/**
 * Turns a raw feed entry into the stored metadata shape (spec 001 R4, R9.1). Pure: no I/O, no
 * framework.
 */
public final class ItemNormaliser {

  public static final int MAX_EXCERPT = 300;
  public static final int MAX_TITLE = 500;
  private static final String ELLIPSIS = "…";

  private final Duration maxAge;

  public ItemNormaliser(Duration maxAge) {
    this.maxAge = maxAge;
  }

  public NormaliseResult normalise(RawEntry raw, Instant fetchedAt) {
    String title = truncate(plainText(raw.title()), MAX_TITLE);
    if (title.isEmpty()) {
      return new NormaliseResult.Malformed("missing title");
    }
    String canonicalUrl = canonicalUrl(raw.link());
    if (canonicalUrl == null) {
      return new NormaliseResult.Malformed("missing or invalid link");
    }

    Instant published = raw.publishedAt() != null ? raw.publishedAt() : raw.updatedAt();
    boolean estimated = published == null;
    if (estimated) {
      published = fetchedAt;
    }
    if (published.isBefore(fetchedAt.minus(maxAge))) {
      return new NormaliseResult.TooOld();
    }

    String excerpt = truncate(plainText(raw.descriptionHtml()), MAX_EXCERPT);
    return new NormaliseResult.Accepted(
        new NormalisedEntry(
            title,
            excerpt.isEmpty() ? null : excerpt,
            canonicalUrl,
            titleKey(title),
            published,
            estimated));
  }

  /** Strips all HTML and collapses whitespace (R4.2). */
  static String plainText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String text = Jsoup.parse(html).text();
    return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
  }

  /** Cuts at a word boundary so the result, including the ellipsis, fits the limit (R4.2). */
  static String truncate(String text, int max) {
    if (text.length() <= max) {
      return text;
    }
    int limit = max - ELLIPSIS.length();
    int cut = text.lastIndexOf(' ', limit);
    if (cut <= 0) {
      cut = limit;
    }
    return text.substring(0, cut).stripTrailing().replaceAll("[\\s,;:.\\-–—]+$", "") + ELLIPSIS;
  }

  /**
   * Lower-cases scheme and host, drops fragments, default ports and tracking parameters (R4.3).
   * Returns null for anything that is not an absolute http(s) URL.
   */
  static String canonicalUrl(String link) {
    if (link == null || link.isBlank()) {
      return null;
    }
    URI uri;
    try {
      uri = new URI(link.trim());
    } catch (URISyntaxException e) {
      return null;
    }
    String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
    if (host == null || !("http".equals(scheme) || "https".equals(scheme))) {
      return null;
    }
    int port = uri.getPort();
    boolean defaultPort =
        port == -1
            || ("http".equals(scheme) && port == 80)
            || ("https".equals(scheme) && port == 443);

    StringBuilder out = new StringBuilder(scheme).append("://").append(host);
    if (!defaultPort) {
      out.append(':').append(port);
    }
    String path = uri.getRawPath();
    out.append(path == null || path.isEmpty() ? "/" : path);

    String query = cleanQuery(uri.getRawQuery());
    if (!query.isEmpty()) {
      out.append('?').append(query);
    }
    return out.toString();
  }

  private static String cleanQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return "";
    }
    List<String> kept = new ArrayList<>();
    for (String pair : rawQuery.split("&")) {
      String name = pair.split("=", 2)[0].toLowerCase(Locale.ROOT);
      if (pair.isEmpty()
          || name.startsWith("utm_")
          || name.equals("fbclid")
          || name.equals("gclid")) {
        continue;
      }
      kept.add(pair);
    }
    return String.join("&", kept);
  }

  /** Lower-case, punctuation-free form of a title used for same-source duplicate checks (R6.2). */
  static String titleKey(String title) {
    return title
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }
}
