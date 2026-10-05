package com.qbits.pages.domain;

import java.util.regex.Pattern;

/**
 * Fills the web app's built {@code index.html} (the shell) with one page's metadata, content and
 * initial data (spec 007 R1.6, R2, R4.1). The shell's script and stylesheet tags are kept as they
 * are, so the page loads the same bundle the build produced.
 */
public final class PageRenderer {

  public static final String SITE_NAME = "Q-Bits";
  static final String ROOT = "<div id=\"root\"></div>";

  private static final Pattern TITLE = Pattern.compile("<title>.*?</title>", Pattern.DOTALL);
  private static final Pattern DESCRIPTION =
      Pattern.compile("\\s*<meta\\s+name=\"description\"[^>]*>", Pattern.CASE_INSENSITIVE);

  private final String head;
  private final String beforeRoot;
  private final String afterRoot;
  private final String siteUrl;

  /**
   * @param shell the built {@code index.html}
   * @param siteUrl canonical origin without a trailing slash, e.g. {@code https://qbitsnews.com}
   * @throws IllegalArgumentException if the shell has no {@code </head>} or empty root element
   */
  public PageRenderer(String shell, String siteUrl) {
    String cleaned = DESCRIPTION.matcher(TITLE.matcher(shell).replaceFirst("")).replaceAll("");
    int headEnd = cleaned.indexOf("</head>");
    int root = cleaned.indexOf(ROOT);
    if (headEnd < 0 || root < 0 || root < headEnd) {
      throw new IllegalArgumentException("shell needs </head> followed by " + ROOT);
    }
    this.head = cleaned.substring(0, headEnd);
    this.beforeRoot = cleaned.substring(headEnd, root);
    this.afterRoot = cleaned.substring(root + ROOT.length());
    this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
  }

  public String siteUrl() {
    return siteUrl;
  }

  /** The absolute URL of a site path. */
  public String url(String path) {
    return siteUrl + path;
  }

  public String render(PageModel page) {
    StringBuilder tags = new StringBuilder(1024);
    tags.append("    <title>").append(Html.text(page.title())).append("</title>\n");
    meta(tags, "name", "description", page.description());
    if (page.noindex()) {
      meta(tags, "name", "robots", "noindex");
    }
    if (!page.canonicalPath().isEmpty()) {
      tags.append("    <link rel=\"canonical\" href=\"")
          .append(Html.text(url(page.canonicalPath())))
          .append("\" />\n");
      meta(tags, "property", "og:url", url(page.canonicalPath()));
    }
    meta(tags, "property", "og:site_name", SITE_NAME);
    meta(tags, "property", "og:type", page.ogType());
    meta(tags, "property", "og:title", page.title());
    meta(tags, "property", "og:description", page.description());
    meta(tags, "name", "twitter:card", "summary");
    for (String ld : page.jsonLd()) {
      tags.append("    <script type=\"application/ld+json\">")
          .append(Html.scriptJson(ld))
          .append("</script>\n");
    }
    if (!page.initialData().isEmpty()) {
      tags.append("    <script id=\"qbits-initial-data\" type=\"application/json\">")
          .append(Html.scriptJson(page.initialData()))
          .append("</script>\n");
    }
    return head
        + tags
        + "  "
        + beforeRoot
        + "<div id=\"root\">"
        + page.body()
        + "</div>"
        + afterRoot;
  }

  private static void meta(StringBuilder out, String attr, String key, String content) {
    out.append("    <meta ")
        .append(attr)
        .append("=\"")
        .append(key)
        .append("\" content=\"")
        .append(Html.text(content))
        .append("\" />\n");
  }
}
