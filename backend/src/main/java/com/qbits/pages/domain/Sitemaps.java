package com.qbits.pages.domain;

import static com.qbits.pages.domain.Html.text;

import com.qbits.catalog.domain.StoryView;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** {@code robots.txt}, the sitemap and the Google News sitemap (spec 007 R6). */
public final class Sitemaps {

  /** Google News lists articles from the last two days only. */
  public static final Duration NEWS_WINDOW = Duration.ofHours(48);

  /** Google News sitemaps hold at most 1,000 URLs. */
  static final int NEWS_MAX = 1000;

  private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

  private Sitemaps() {}

  /** One sitemap URL; {@code lastmod} may be null when unknown. */
  public record UrlEntry(String path, Instant lastmod) {}

  public static String robots(String siteUrl) {
    return """
        User-agent: *
        Allow: /
        Disallow: /api/
        Disallow: /login
        Disallow: /register

        Sitemap: %1$s/sitemap.xml
        Sitemap: %1$s/sitemap-news.xml
        """
        .formatted(siteUrl);
  }

  public static String sitemap(String siteUrl, List<UrlEntry> entries) {
    StringBuilder xml = new StringBuilder(XML);
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    for (UrlEntry e : entries) {
      xml.append("  <url><loc>").append(text(siteUrl + e.path())).append("</loc>");
      if (e.lastmod() != null) {
        xml.append("<lastmod>").append(stamp(e.lastmod())).append("</lastmod>");
      }
      xml.append("</url>\n");
    }
    return xml.append("</urlset>\n").toString();
  }

  /** Stories published in the 48 hours before {@code now}, newest first as given. */
  public static String newsSitemap(String siteUrl, List<StoryView> stories, Instant now) {
    Instant from = now.minus(NEWS_WINDOW);
    StringBuilder xml = new StringBuilder(XML);
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")
        .append(" xmlns:news=\"http://www.google.com/schemas/sitemap-news/0.9\">\n");
    stories.stream()
        .filter(s -> !s.publishedAt().isBefore(from))
        .limit(NEWS_MAX)
        .forEach(
            s ->
                xml.append("  <url><loc>")
                    .append(text(siteUrl + "/story/" + s.id()))
                    .append("</loc><news:news><news:publication><news:name>")
                    .append(PageRenderer.SITE_NAME)
                    .append("</news:name><news:language>en</news:language></news:publication>")
                    .append("<news:publication_date>")
                    .append(stamp(s.publishedAt()))
                    .append("</news:publication_date><news:title>")
                    .append(text(s.headline()))
                    .append("</news:title></news:news></url>\n"));
    return xml.append("</urlset>\n").toString();
  }

  private static String stamp(Instant at) {
    return at.truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
