package com.qbits.pages.domain;

import static com.qbits.pages.domain.PageFixtures.SHELL;
import static com.qbits.pages.domain.PageFixtures.SITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageRendererTest {

  private final PageRenderer renderer = new PageRenderer(SHELL, SITE);

  private static PageModel page() {
    return PageModel.page(
        "Chips & <models> | Q-Bits",
        "A \"quoted\" description",
        "/story/abc",
        "article",
        List.of("{\"@type\":\"NewsArticle\",\"headline\":\"</script>\"}"),
        "<h1>Server content</h1>",
        "{\"queries\":[]}");
  }

  @Test
  void keepsTheBuiltBundle() { // 007 R1.6
    String html = renderer.render(page());

    assertThat(html)
        .contains("<script type=\"module\" crossorigin src=\"/assets/index-abc123.js\"></script>")
        .contains("<link rel=\"stylesheet\" crossorigin href=\"/assets/index-def456.css\">")
        .contains("<meta charset=\"UTF-8\" />")
        .startsWith("<!doctype html>")
        .endsWith("</html>\n");
  }

  @Test
  void replacesTitleAndDescriptionOnce() { // 007 R2.1, R2.2, R2.7
    String html = renderer.render(page());

    assertThat(html).contains("<title>Chips &amp; &lt;models&gt; | Q-Bits</title>");
    assertThat(html).doesNotContain("<title>Q-Bits</title>", "old description");
    assertThat(html.split("<title>", -1)).hasSize(2);
    assertThat(html).contains("<meta name=\"description\" content=\"A &quot;quoted&quot;");
  }

  @Test
  void addsCanonicalAndSocialTags() { // 007 R2.3, R2.4
    String html = renderer.render(page());

    assertThat(html)
        .contains("<link rel=\"canonical\" href=\"https://qbitsnews.example/story/abc\" />")
        .contains("<meta property=\"og:url\" content=\"https://qbitsnews.example/story/abc\" />")
        .contains("<meta property=\"og:type\" content=\"article\" />")
        .contains("<meta property=\"og:site_name\" content=\"Q-Bits\" />")
        .contains("<meta property=\"og:title\" content=\"Chips &amp; &lt;models&gt; | Q-Bits\" />")
        .contains("<meta name=\"twitter:card\" content=\"summary\" />")
        .doesNotContain("og:image", "noindex");
  }

  @Test
  void putsContentInsideTheRoot() { // 007 R1.1
    assertThat(renderer.render(page())).contains("<div id=\"root\"><h1>Server content</h1></div>");
  }

  @Test
  void embedsJsonLdAndInitialDataSafely() { // 007 R2.5, R2.7, R4.1
    String html = renderer.render(page());

    assertThat(html)
        .contains("<script type=\"application/ld+json\">{\"@type\":\"NewsArticle\"")
        .contains("\\u003c/script>")
        .contains(
            "<script id=\"qbits-initial-data\" type=\"application/json\">{\"queries\":[]}"
                + "</script>");
    assertThat(html.split("</script>", -1)).hasSize(4); // bundle, JSON-LD, initial data
  }

  @Test
  void appOnlyPagesAreNoindexWithoutCanonicalOrData() { // 007 R2.8, R3
    String html = renderer.render(PageModel.appOnly(404, PageTitles.NOT_FOUND, "Gone"));

    assertThat(html)
        .contains("<meta name=\"robots\" content=\"noindex\" />")
        .contains("<div id=\"root\"></div>")
        .doesNotContain("rel=\"canonical\"", "og:url", "qbits-initial-data", "ld+json");
  }

  @Test
  void trimsATrailingSlashFromTheSiteUrl() { // 007 R2.3
    assertThat(new PageRenderer(SHELL, SITE + "/").url("/about"))
        .isEqualTo("https://qbitsnews.example/about");
  }

  @Test
  void rejectsAShellWithoutARoot() {
    assertThatThrownBy(() -> new PageRenderer("<html><head></head><body></body></html>", SITE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
