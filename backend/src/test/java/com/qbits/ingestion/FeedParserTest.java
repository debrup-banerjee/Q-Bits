package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qbits.ingestion.domain.RawEntry;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedParserTest {

  private final FeedParser parser = new FeedParser();

  @Test
  void readsRssEntries() {
    List<RawEntry> entries = parser.parse(fixture("rss-sample.xml"));

    assertThat(entries).hasSize(3);
    RawEntry first = entries.getFirst();
    assertThat(first.title()).isEqualTo("New AI model reads whole books at once");
    assertThat(first.link()).isEqualTo("https://news.example.com/ai-model?utm_source=rss");
    assertThat(first.descriptionHtml()).contains("large language model");
    assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-10-03T04:00:00Z"));
  }

  @Test
  void readsAtomEntries() {
    RawEntry entry = parser.parse(fixture("atom-sample.xml")).getFirst();

    assertThat(entry.link()).isEqualTo("https://lab.example.org/robots-laundry");
    assertThat(entry.descriptionHtml())
        .isEqualTo("Researchers trained a robot arm using machine learning.");
    assertThat(entry.updatedAt()).isEqualTo(Instant.parse("2026-10-03T05:00:00Z"));
  }

  @Test
  void neverReadsFullContentElements() { // principles: no article bodies
    List<RawEntry> entries = parser.parse(fixture("rss-sample.xml"));
    assertThat(entries)
        .noneMatch(e -> String.valueOf(e.descriptionHtml()).contains("FULL ARTICLE"));
    assertThat(parser.parse(fixture("atom-sample.xml")))
        .noneMatch(e -> String.valueOf(e.descriptionHtml()).contains("FULL ARTICLE"));
  }

  @Test
  void collectsLinksFromEntryDescriptionAndContentWithoutText() { // 005 R1.1, R1.2
    RawEntry entry = parser.parse(fixture("rss-links.xml")).getFirst();

    assertThat(entry.links())
        .extracting(l -> l.url() + " " + l.origin())
        .containsExactly(
            "https://lab.example.org/kestrel ENTRY_LINK",
            "https://huggingface.co/kestrel-ai/Kestrel-70B DESCRIPTION",
            "https://arxiv.org/abs/2410.01234v2 DESCRIPTION",
            "https://github.com/kestrel-ai/kestrel/tree/main CONTENT",
            "https://github.com/newsco CONTENT",
            "https://github.com/newsco/share-widgets CONTENT",
            "https://huggingface.co/blog/kestrel CONTENT");
    assertThat(entry.toString()).doesNotContain("FULL ARTICLE");
  }

  @Test
  void rejectsNonFeedContent() {
    assertThatThrownBy(() -> parser.parse("<html><body>hi</body></html>".getBytes()))
        .isInstanceOf(FeedParser.FeedParseException.class);
  }

  static byte[] fixture(String name) {
    try (InputStream in = FeedParserTest.class.getResourceAsStream("/feeds/" + name)) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] rss(String item) {
    return ("""
        <?xml version="1.0"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel><title>T</title><link>https://lab.example/</link><description>D</description>
            <item><title>A</title><link>https://lab.example/a</link>%s</item>
          </channel>
        </rss>
        """
            .formatted(item))
        .strip()
        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void readsTheImageTheFeedAttachesToAnEntry() { // 009 R3.2
    assertThat(
            parser
                .parse(
                    rss(
                        "<enclosure url=\"https://lab.example/e.jpg\" type=\"image/jpeg\" length=\"1\"/>"))
                .getFirst()
                .imageUrl())
        .isEqualTo("https://lab.example/e.jpg");
    assertThat(
            parser
                .parse(
                    rss(
                        "<media:thumbnail url=\"https://lab.example/t.jpg\"/>"
                            + "<media:group><media:content url=\"https://lab.example/c.jpg\""
                            + " medium=\"image\"/></media:group>"))
                .getFirst()
                .imageUrl())
        .isEqualTo("https://lab.example/c.jpg");
    assertThat(
            parser
                .parse(rss("<media:thumbnail url=\"https://lab.example/t.jpg\"/>"))
                .getFirst()
                .imageUrl())
        .isEqualTo("https://lab.example/t.jpg");
  }

  @Test
  void ignoresNonHttpsAndNonImageMedia() { // 009 R3.2
    assertThat(
            parser
                .parse(
                    rss(
                        "<enclosure url=\"https://lab.example/a.mp3\" type=\"audio/mpeg\" length=\"1\"/>"
                            + "<media:content url=\"http://lab.example/c.jpg\" medium=\"image\"/>"
                            + "<description>&lt;img src=\"https://lab.example/inline.jpg\"&gt;</description>"))
                .getFirst()
                .imageUrl())
        .isNull();
  }
}
