package com.qbits.pages.domain;

import com.qbits.catalog.domain.StoryView;
import com.qbits.resources.domain.ResourceLink;
import com.qbits.stories.domain.KeyTerm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stories for page tests. */
final class PageFixtures {

  static final String SITE = "https://qbitsnews.example";

  static final String SHELL =
      """
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="description" content="old description" />
          <title>Q-Bits</title>
          <script type="module" crossorigin src="/assets/index-abc123.js"></script>
          <link rel="stylesheet" crossorigin href="/assets/index-def456.css">
        </head>
        <body>
          <div id="root"></div>
        </body>
      </html>
      """;

  private PageFixtures() {}

  static StoryView story(String headline, String summary, Instant publishedAt) {
    return new StoryView(
        UUID.fromString("0192f000-0000-7000-8000-000000000001"),
        new StoryView.SectionRef("india-ai", "India AI"),
        headline,
        summary,
        List.of(new KeyTerm("GPU", "A chip that does many small sums at once.")),
        new StoryView.SourceRef("Example Lab", "https://lab.example.org"),
        "https://news.example/a?x=1&y=2",
        publishedAt,
        false,
        StoryView.attributionFor("Example Lab"),
        List.of(
            new ResourceLink(
                "code", "Code on GitHub", "https://github.com/example/repo", "example/repo")));
  }

  static StoryView story() {
    return story(
        "India launches a new AI model",
        "An Indian lab released a model that reads twelve languages.",
        Instant.parse("2026-10-05T01:30:00Z"));
  }
}
