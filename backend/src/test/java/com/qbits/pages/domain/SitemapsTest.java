package com.qbits.pages.domain;

import static com.qbits.pages.domain.PageFixtures.SITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class SitemapsTest {

  private static final Instant NOW = Instant.parse("2026-10-05T06:00:00Z");

  @Test
  void robotsAllowsPagesAndNamesBothSitemaps() { // 007 R6.1
    String robots = Sitemaps.robots(SITE);

    assertThat(robots.lines())
        .contains(
            "User-agent: *",
            "Allow: /",
            "Disallow: /api/",
            "Disallow: /login",
            "Disallow: /register",
            "Sitemap: https://qbitsnews.example/sitemap.xml",
            "Sitemap: https://qbitsnews.example/sitemap-news.xml");
  }

  @Test
  void sitemapListsUrlsWithLastmod() throws Exception { // 007 R6.2, R6.4
    String xml =
        Sitemaps.sitemap(
            SITE,
            List.of(
                new Sitemaps.UrlEntry("/", Instant.parse("2026-10-05T00:42:00.123Z")),
                new Sitemaps.UrlEntry("/about", null),
                new Sitemaps.UrlEntry("/story/a?b=1&c=2", NOW)));

    Document doc = parse(xml);

    assertThat(doc.getElementsByTagName("url").getLength()).isEqualTo(3);
    assertThat(doc.getElementsByTagName("loc").item(2).getTextContent())
        .isEqualTo(SITE + "/story/a?b=1&c=2");
    assertThat(xml)
        .contains("<loc>https://qbitsnews.example/</loc><lastmod>2026-10-05T00:42:00Z</lastmod>")
        .contains("<loc>https://qbitsnews.example/about</loc></url>");
  }

  @Test
  void newsSitemapHasOnlyTheLast48Hours() throws Exception { // 007 R6.3, R6.4
    var fresh = PageFixtures.story("Fresh & new", "s", NOW.minus(java.time.Duration.ofHours(47)));
    var old = PageFixtures.story("Old", "s", NOW.minus(java.time.Duration.ofHours(49)));

    String xml = Sitemaps.newsSitemap(SITE, List.of(fresh, old), NOW);

    Document doc = parse(xml);
    assertThat(doc.getElementsByTagName("url").getLength()).isEqualTo(1);
    assertThat(doc.getElementsByTagName("news:title").item(0).getTextContent())
        .isEqualTo("Fresh & new");
    assertThat(xml)
        .contains("<news:name>Q-Bits</news:name><news:language>en</news:language>")
        .contains("<news:publication_date>2026-10-03T07:00:00Z</news:publication_date>");
  }

  private static Document parse(String xml) throws Exception {
    return DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
