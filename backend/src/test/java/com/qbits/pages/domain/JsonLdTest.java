package com.qbits.pages.domain;

import static com.qbits.pages.domain.PageFixtures.SITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonLdTest {

  @Test
  void storyIsANewsArticleBasedOnTheOriginal() { // 007 R2.5
    String url = SITE + "/story/x";

    Map<String, Object> ld = JsonLd.newsArticle(PageFixtures.story(), url, SITE);

    assertThat(ld)
        .containsEntry("@context", "https://schema.org")
        .containsEntry("@type", "NewsArticle")
        .containsEntry("headline", "India launches a new AI model")
        .containsEntry("description", "An Indian lab released a model that reads twelve languages.")
        .containsEntry("datePublished", "2026-10-05T01:30:00Z")
        .containsEntry("url", url)
        .containsEntry("isBasedOn", "https://news.example/a?x=1&y=2")
        .containsEntry("articleSection", "India AI")
        .doesNotContainKey("image");
    Map<String, Object> org = Map.of("@type", "Organization", "name", "Q-Bits", "url", SITE + "/");
    assertThat(ld.get("author")).isEqualTo(org);
    assertThat(ld.get("publisher")).isEqualTo(org);
  }

  @Test
  void homeDescribesTheSiteAndOrganization() { // 007 R2.6
    assertThat(JsonLd.webSite(SITE))
        .containsEntry("@type", "WebSite")
        .containsEntry("name", "Q-Bits")
        .containsEntry("url", SITE + "/");
    assertThat(JsonLd.organization(SITE))
        .containsEntry("@context", "https://schema.org")
        .containsEntry("@type", "Organization")
        .doesNotContainKey("logo");
  }
}
