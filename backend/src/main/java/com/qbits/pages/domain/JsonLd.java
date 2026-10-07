package com.qbits.pages.domain;

import com.qbits.catalog.domain.StoryView;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * schema.org structured data as plain maps, ready to serialise (spec 007 R2.5, R2.6). No images or
 * logos (principles).
 */
public final class JsonLd {

  private static final String CONTEXT = "https://schema.org";

  private JsonLd() {}

  /**
   * A story as a {@code NewsArticle}. {@code datePublished} is the source's publish time; {@code
   * isBasedOn} points to the original article.
   */
  public static Map<String, Object> newsArticle(StoryView s, String pageUrl, String siteUrl) {
    Map<String, Object> ld = new LinkedHashMap<>();
    ld.put("@context", CONTEXT);
    ld.put("@type", "NewsArticle");
    ld.put("headline", s.headline());
    ld.put("description", s.summary());
    ld.put("datePublished", s.publishedAt().toString());
    ld.put("url", pageUrl);
    ld.put("mainEntityOfPage", pageUrl);
    ld.put("isBasedOn", s.originalUrl());
    ld.put("articleSection", s.section().name());
    ld.put("inLanguage", "en");
    ld.put("author", organizationRef(siteUrl));
    ld.put("publisher", organizationRef(siteUrl));
    return ld;
  }

  public static Map<String, Object> webSite(String siteUrl) {
    Map<String, Object> ld = new LinkedHashMap<>();
    ld.put("@context", CONTEXT);
    ld.put("@type", "WebSite");
    ld.put("name", PageRenderer.SITE_NAME);
    ld.put("url", siteUrl + "/");
    ld.put("inLanguage", "en");
    return ld;
  }

  public static Map<String, Object> organization(String siteUrl) {
    Map<String, Object> ld = new LinkedHashMap<>();
    ld.put("@context", CONTEXT);
    ld.putAll(organizationRef(siteUrl));
    return ld;
  }

  private static Map<String, Object> organizationRef(String siteUrl) {
    Map<String, Object> org = new LinkedHashMap<>();
    org.put("@type", "Organization");
    org.put("name", PageRenderer.SITE_NAME);
    org.put("url", siteUrl + "/");
    return org;
  }
}
