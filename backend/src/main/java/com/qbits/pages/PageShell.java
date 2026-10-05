package com.qbits.pages;

import com.qbits.pages.domain.PageRenderer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The web build's {@code index.html}, read once at startup (spec 007 R1.6). The deploy copies the
 * web build before restarting the backend, so a restart always picks up the new asset names.
 */
@Component
public class PageShell {

  private static final Logger log = LoggerFactory.getLogger(PageShell.class);

  /** Used only when the web build is missing: the page has metadata but no app bundle. */
  static final String FALLBACK =
      """
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        </head>
        <body>
          <div id="root"></div>
        </body>
      </html>
      """;

  private final PageRenderer renderer;
  private final boolean fromBuild;

  public PageShell(PagesProperties props) {
    Path file = Path.of(props.shellFile());
    PageRenderer built = null;
    try {
      built = new PageRenderer(Files.readString(file, StandardCharsets.UTF_8), props.siteUrl());
      log.info("page shell loaded file={}", file.toAbsolutePath());
    } catch (IOException | IllegalArgumentException e) {
      log.warn(
          "page shell unusable, pages will not load the web app file={} error={}",
          file.toAbsolutePath(),
          e.getClass().getSimpleName());
    }
    this.fromBuild = built != null;
    this.renderer = built != null ? built : new PageRenderer(FALLBACK, props.siteUrl());
  }

  public PageRenderer renderer() {
    return renderer;
  }

  /** False when the web build could not be read and the fallback shell is in use. */
  public boolean fromBuild() {
    return fromBuild;
  }
}
