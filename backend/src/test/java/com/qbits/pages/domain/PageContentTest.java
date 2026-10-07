package com.qbits.pages.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.catalog.domain.StoryView;
import com.qbits.stories.domain.Section;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PageContentTest {

  @Test
  void storyPageShowsEveryReaderField() { // 007 R1.1
    String html = PageContent.storyPage(PageFixtures.story());

    assertThat(html)
        .contains("<h1 class=\"text-lg font-bold leading-snug sm:text-xl\">")
        .contains(">India launches a new AI model</a></h1>")
        .contains("An Indian lab released a model that reads twelve languages.")
        .contains("<dt class=\"inline font-semibold\">GPU</dt>")
        .contains("A chip that does many small sums at once.")
        .contains("<span>Example Lab</span>")
        .contains("href=\"https://news.example/a?x=1&amp;y=2\"")
        .contains("Read the full story at Example Lab")
        .contains("Summary written from Example Lab&#39;s headline and teaser")
        .contains("<time datetime=\"2026-10-05T01:30:00Z\">5 Oct 2026, 01:30 UTC</time>")
        .contains("href=\"https://github.com/example/repo\"")
        .contains("rel=\"noopener noreferrer\"");
  }

  @Test
  void storyTextIsEscaped() { // 007 R2.7
    StoryView s =
        PageFixtures.story(
            "<script>alert(1)</script>", "Tom & Jerry", Instant.parse("2026-10-05T01:30:00Z"));

    String html = PageContent.storyPage(s);

    assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;", "Tom &amp; Jerry");
  }

  @Test
  void pagesLinkToEverySection() { // 007 R1.2 (internal links for crawlers)
    String html = PageContent.latestPage(List.of());

    for (Section s : Section.values()) {
      assertThat(html).contains("href=\"/section/" + s.slug() + "\"");
    }
    assertThat(html).contains("href=\"/sections\"", "href=\"/about\"");
  }

  @Test
  void latestPageListsStoriesUnderAnAiLatestHeading() { // 007 R1.3
    String html = PageContent.latestPage(List.of(PageFixtures.story()));

    assertThat(html)
        .contains(">AI Latest</h1>")
        .contains("<h2 class=\"text-[17px] font-semibold leading-snug sm:text-lg\">")
        .contains("href=\"/story/0192f000-0000-7000-8000-000000000001\"")
        .contains("An Indian lab released a model");
  }

  @Test
  void sectionPageShowsNameDescriptionAndStories() { // 007 R1.2
    String html = PageContent.sectionPage(Section.INDIA_AI, List.of(PageFixtures.story()));

    assertThat(html)
        .contains(">India AI</h1>")
        .contains(Html.text(Section.INDIA_AI.description()))
        .contains("<h2 class=\"text-lg font-bold leading-snug sm:text-xl\">");
  }

  @Test
  void emptySectionSaysSo() { // 007 R1.2
    assertThat(PageContent.sectionPage(Section.INDIA_AI, List.of()))
        .contains("No India AI stories in the last 72 hours.");
  }

  @Test
  void overviewShowsEachSectionWithItsStories() { // 007 R1.4
    Map<Section, List<StoryView>> by = new LinkedHashMap<>();
    by.put(Section.GLOBAL_AI_TECH, List.of());
    by.put(Section.INDIA_AI, List.of(PageFixtures.story()));

    String html = PageContent.overviewPage(by);

    assertThat(html)
        .contains(">Browse by section</h1>")
        .contains("sorted into 2 sections")
        .contains(">" + Section.GLOBAL_AI_TECH.displayName() + "</a></h2>")
        .contains("<h3 class=\"text-lg font-bold leading-snug sm:text-xl\">");
    assertThat(html.indexOf(Section.GLOBAL_AI_TECH.displayName() + "</a></h2>"))
        .isLessThan(html.indexOf("India AI</a></h2>"));
  }

  @Test
  void shortDescriptionsAreKept() { // 007 R2.2
    assertThat(PageContent.describe("  A short\n summary. ")).isEqualTo("A short summary.");
  }

  @Test
  void longDescriptionsAreCutAtAWord() { // 007 R2.2
    String summary = "word ".repeat(60).strip();

    String d = PageContent.describe(summary);

    assertThat(d).hasSizeLessThanOrEqualTo(PageContent.DESCRIPTION_MAX).endsWith("word…");
  }

  @Test
  void showsThePictureWithItsCredit() { // 009 R5.1, R5.2
    StoryView base = PageFixtures.story();
    StoryView withPhoto =
        new StoryView(
            base.id(),
            base.section(),
            base.headline(),
            base.summary(),
            base.keyTerms(),
            base.source(),
            base.originalUrl(),
            base.publishedAt(),
            base.dateEstimated(),
            base.attribution(),
            base.resources(),
            new com.qbits.images.domain.StoryImage(
                "photo",
                "https://images.pexels.com/p?a=1&b=2",
                "a \"chip\"",
                "Asha Rao",
                "https://www.pexels.com/@asha",
                "Pexels",
                "https://www.pexels.com",
                "#112233"));

    assertThat(PageContent.storyPage(withPhoto))
        .contains("<img src=\"https://images.pexels.com/p?a=1&amp;b=2\" alt=\"a &quot;chip&quot;\"")
        .contains("Illustrative photo by Asha Rao on Pexels");
    assertThat(PageContent.storyPage(base)).doesNotContain("<img");
  }
}
