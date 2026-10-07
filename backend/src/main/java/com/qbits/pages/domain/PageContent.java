package com.qbits.pages.domain;

import static com.qbits.pages.domain.Html.text;

import com.qbits.catalog.domain.StoryView;
import com.qbits.images.domain.StoryImage;
import com.qbits.resources.domain.ResourceLink;
import com.qbits.stories.domain.KeyTerm;
import com.qbits.stories.domain.Section;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Readable HTML for the first response of each page (spec 007 R1.1–R1.4). Uses the same element
 * structure and main classes as the web app's components, so the switch to React's render is close
 * to seamless. Built only from {@link StoryView} fields, which never carry the publisher's title or
 * excerpt (R1.7). Every value is escaped.
 */
public final class PageContent {

  public static final int DESCRIPTION_MAX = 160;

  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm 'UTC'", Locale.ENGLISH)
          .withZone(ZoneOffset.UTC);

  private static final String NAV_LINK =
      "inline-flex items-center whitespace-nowrap rounded-full px-3 py-1.5 text-muted hover:bg-chip hover:text-ink";

  private PageContent() {}

  /** One story on its own page (R1.1). */
  public static String storyPage(StoryView story) {
    return layout("<div class=\"mx-auto max-w-3xl\">" + card(story, 1) + "</div>");
  }

  /** AI Latest: the first page of the latest edition (R1.3). */
  public static String latestPage(List<StoryView> stories) {
    StringBuilder main = new StringBuilder("<div class=\"mx-auto flex max-w-2xl flex-col gap-5\">");
    main.append(
        hero("AI Latest", "Daily digest", "Every AI story from the last 24 hours, newest first"));
    if (stories.isEmpty()) {
      main.append("<p class=\"py-8 text-muted\">Today's edition has no stories yet.</p>");
    } else {
      main.append("<div class=\"card overflow-hidden px-4 sm:px-6\">");
      for (StoryView s : stories) {
        main.append(feedItem(s));
      }
      main.append("</div>");
    }
    main.append("<p class=\"py-4 text-center text-sm\"><a href=\"/sections\"")
        .append(" class=\"link-arrow\">Browse by section</a></p>")
        .append("</div>");
    return layout(main.toString());
  }

  /** One section's first page of stories (R1.2). */
  public static String sectionPage(Section section, List<StoryView> stories) {
    StringBuilder main = new StringBuilder("<div class=\"mx-auto flex max-w-3xl flex-col gap-5\">");
    main.append(hero(section.displayName(), "Last 72 hours", section.description()));
    if (stories.isEmpty()) {
      main.append("<p class=\"py-8 text-muted\">No ")
          .append(text(section.displayName()))
          .append(" stories in the last 72 hours.</p>");
    }
    for (StoryView s : stories) {
      main.append(card(s, 2));
    }
    main.append("</div>");
    return layout(main.toString());
  }

  /** Every section with its newest stories, in display order (R1.4). */
  public static String overviewPage(Map<Section, List<StoryView>> bySection) {
    StringBuilder main = new StringBuilder("<div class=\"flex flex-col gap-10\">");
    main.append(
        hero(
            "Browse by section",
            "Last 72 hours",
            "AI news from the last 72 hours, sorted into " + bySection.size() + " sections."));
    bySection.forEach(
        (section, stories) -> {
          main.append("<section class=\"flex flex-col gap-4\"><div class=\"border-b border-line")
              .append(" pb-3\"><h2 class=\"text-xl font-bold sm:text-2xl\"><a href=\"/section/")
              .append(text(section.slug()))
              .append("\" class=\"hover:underline\">")
              .append(text(section.displayName()))
              .append("</a></h2><p class=\"text-sm text-muted\">")
              .append(text(section.description()))
              .append("</p></div>");
          for (StoryView s : stories) {
            main.append(card(s, 3));
          }
          main.append("</section>");
        });
    main.append("</div>");
    return layout(main.toString());
  }

  /** A summary cut to at most 160 characters at a word boundary, for meta descriptions (R2.2). */
  public static String describe(String value) {
    String flat = value.strip().replaceAll("\\s+", " ");
    if (flat.length() <= DESCRIPTION_MAX) {
      return flat;
    }
    int cut = flat.lastIndexOf(' ', DESCRIPTION_MAX - 1);
    String head = flat.substring(0, cut > 0 ? cut : DESCRIPTION_MAX - 1);
    return head.replaceAll("[\\s,;:.-]+$", "") + "…";
  }

  /** The gradient page header, like the web app's {@code PageHero}. Values are escaped here. */
  static String hero(String title, String eyebrow, String subtitle) {
    return "<div class=\"page-hero\"><p class=\"hero-eyebrow\">"
        + text(eyebrow)
        + "</p><h1>"
        + text(title)
        + "</h1><div class=\"hero-sub\"><p>"
        + text(subtitle)
        + "</p></div></div>";
  }

  /** Header, section links, the page's content and the footer, like the web app's shell. */
  static String layout(String main) {
    StringBuilder html =
        new StringBuilder("<div class=\"relative isolate flex min-h-dvh flex-col\">");
    html.append("<div aria-hidden=\"true\" class=\"page-backdrop\"></div>")
        .append("<header class=\"site-header z-20 sm:sticky sm:top-0\">")
        .append("<div aria-hidden=\"true\" class=\"brand-line\"></div>")
        .append("<div class=\"mx-auto flex max-w-6xl flex-wrap items-center justify-between")
        .append(" gap-3 px-4 py-3\">")
        .append("<a href=\"/\" class=\"flex items-center gap-2.5 no-underline\">")
        .append("<span aria-hidden=\"true\" class=\"brand-mark\">Q</span>")
        .append("<span class=\"text-xl font-bold tracking-tight text-ink\">Q-Bits</span>")
        .append("<span class=\"hidden text-sm font-medium text-muted min-[400px]:inline\">")
        .append("Unlocking AI</span></a>")
        .append("<nav aria-label=\"Sections\"><ul class=\"flex flex-wrap gap-1 text-sm")
        .append(" font-semibold\"><li><a href=\"/\" class=\"")
        .append(NAV_LINK)
        .append("\">AI Latest</a></li>");
    for (Section s : Section.values()) {
      html.append("<li><a href=\"/section/")
          .append(text(s.slug()))
          .append("\" class=\"")
          .append(NAV_LINK)
          .append("\">")
          .append(text(s.displayName()))
          .append("</a></li>");
    }
    html.append("</ul></nav></div></header>")
        .append("<main id=\"main\" class=\"mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-10\">")
        .append(main)
        .append("</main><footer class=\"mt-10 border-t border-line bg-surface\">")
        .append("<div class=\"mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10")
        .append(" sm:flex-row sm:justify-between\">")
        .append("<p class=\"max-w-sm text-sm text-muted\">Plain-language AI news from the last")
        .append(" three days. Every story links to its original publisher.</p>")
        .append("<nav aria-label=\"Footer\" class=\"flex flex-col gap-2 text-sm\">")
        .append("<a href=\"/sections\" class=\"link-arrow\">Browse by section</a>")
        .append("<a href=\"/about\" class=\"link-arrow\">How Q-Bits works</a>")
        .append("<a href=\"https://www.pexels.com\" class=\"text-muted underline\"")
        .append(" target=\"_blank\" rel=\"noopener noreferrer\">Photos provided by Pexels</a>")
        .append("</nav></div></footer></div>");
    return html.toString();
  }

  /** A full story card, like the web app's {@code StoryCard}. */
  static String card(StoryView s, int headingLevel) {
    String h = "h" + headingLevel;
    StringBuilder html =
        new StringBuilder("<article class=\"card flex flex-col gap-3.5 p-5 sm:p-6\">");
    html.append(picture(s.image()));
    html.append("<div class=\"flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-medium")
        .append(" uppercase tracking-wide text-muted\">")
        .append(sectionLink(s))
        .append("<span aria-hidden=\"true\">·</span><span>")
        .append(text(s.source().name()))
        .append("</span><span aria-hidden=\"true\">·</span>")
        .append(time(s.publishedAt()))
        .append("</div><")
        .append(h)
        .append(" class=\"text-lg font-bold leading-snug sm:text-xl\">")
        .append(storyLink(s))
        .append("</")
        .append(h)
        .append("><p class=\"max-w-[65ch] text-base leading-relaxed\">")
        .append(text(s.summary()))
        .append("</p>")
        .append(wordsToKnow(s.keyTerms()))
        .append(resources(s.resources()))
        .append(readMore(s))
        .append("</article>");
    return html.toString();
  }

  /** A compact timeline item, like the web app's {@code FeedCard}. */
  static String feedItem(StoryView s) {
    return "<article class=\"flex flex-col gap-1.5 border-b border-line py-5 last:border-b-0\">"
        + picture(s.image())
        + "<div class=\"flex flex-wrap items-center gap-x-1.5 text-sm text-muted\">"
        + "<span class=\"font-semibold text-ink\">"
        + text(s.source().name())
        + "</span><span aria-hidden=\"true\">·</span>"
        + time(s.publishedAt())
        + "<span aria-hidden=\"true\">·</span>"
        + sectionLink(s)
        + "</div><h2 class=\"text-[17px] font-semibold leading-snug sm:text-lg\">"
        + storyLink(s)
        + "</h2><p class=\"text-base leading-relaxed\">"
        + text(s.summary())
        + "</p>"
        + resources(s.resources())
        + readMore(s)
        + "</article>";
  }

  /**
   * The story's picture with its credit (spec 009 R5), like the web app's {@code StoryPicture}.
   * Stories without one get no picture here; the web app draws their cover art once it loads.
   */
  static String picture(StoryImage image) {
    if (image == null) {
      return "";
    }
    return "<figure class=\"story-figure\"><img src=\""
        + text(image.url())
        + "\" alt=\""
        + text(image.alt())
        + "\" loading=\"lazy\" decoding=\"async\" referrerpolicy=\"no-referrer\""
        + " class=\"story-img\" /><figcaption class=\"story-credit\">"
        + text(creditLine(image))
        + "</figcaption></figure>";
  }

  /** "Illustrative photo by X on Pexels" or "Image: credit" (spec 009 R5.2). */
  public static String creditLine(StoryImage image) {
    return "photo".equals(image.kind())
        ? "Illustrative photo by " + image.credit() + " on " + image.provider()
        : "Image: " + image.credit();
  }

  private static String sectionLink(StoryView s) {
    return "<a href=\"/section/"
        + text(s.section().slug())
        + "\" class=\"font-medium text-accent hover:underline\">"
        + text(s.section().name())
        + "</a>";
  }

  private static String storyLink(StoryView s) {
    return "<a href=\"/story/"
        + s.id()
        + "\" class=\"hover:underline\">"
        + text(s.headline())
        + "</a>";
  }

  private static String time(Instant at) {
    return "<time datetime=\"" + at + "\">" + WHEN.format(at) + "</time>";
  }

  private static String wordsToKnow(List<KeyTerm> terms) {
    if (terms.isEmpty()) {
      return "";
    }
    StringBuilder html =
        new StringBuilder(
            "<details open class=\"rounded-xl border border-line bg-chip/60 px-3.5 py-2.5\"><summary"
                + " class=\"cursor-pointer text-sm font-semibold\">Words to know</summary>"
                + "<dl class=\"mt-2 space-y-1.5 text-sm\">");
    for (KeyTerm t : terms) {
      html.append("<div><dt class=\"inline font-semibold\">")
          .append(text(t.term()))
          .append("</dt><dd class=\"inline text-muted\"> — ")
          .append(text(t.meaning()))
          .append("</dd></div>");
    }
    return html.append("</dl></details>").toString();
  }

  private static String resources(List<ResourceLink> links) {
    if (links.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder("<ul class=\"flex flex-wrap gap-2 text-sm\">");
    for (ResourceLink r : links) {
      html.append("<li><a href=\"")
          .append(text(r.url()))
          .append("\" target=\"_blank\" rel=\"noopener noreferrer\"")
          .append(" class=\"font-medium text-accent hover:underline\">")
          .append(text(r.label()))
          .append("</a></li>");
    }
    return html.append("</ul>").toString();
  }

  private static String readMore(StoryView s) {
    String source = text(s.source().name());
    return "<div><a href=\""
        + text(s.originalUrl())
        + "\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"btn-primary\">"
        + "Read the full story at "
        + source
        + "<span aria-hidden=\"true\">↗</span></a></div><p class=\"text-xs text-muted\">"
        + text(s.attribution())
        + "</p>";
  }
}
