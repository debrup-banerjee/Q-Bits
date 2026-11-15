package com.qbits.pages.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The web app checks the same fixture, so titles match during navigation (007 R2.1, R5.1). */
class PageTitlesTest {

  private static final JsonNode TITLES =
      JsonMapper.builder()
          .build()
          .readTree(Path.of("..", "specs", "007-seo-pages", "titles.json").toFile());

  @Test
  void fixedTitlesMatchTheSharedFixture() { // 007 R2.1
    assertThat(PageTitles.HOME).isEqualTo(TITLES.get("home").asString());
    assertThat(PageTitles.SECTIONS).isEqualTo(TITLES.get("sections").asString());
    assertThat(PageTitles.ABOUT).isEqualTo(TITLES.get("about").asString());
    assertThat(PageTitles.LOGIN).isEqualTo(TITLES.get("login").asString());
    assertThat(PageTitles.REGISTER).isEqualTo(TITLES.get("register").asString());
    assertThat(PageTitles.STORY_NOT_FOUND).isEqualTo(TITLES.get("storyNotFound").asString());
    assertThat(PageTitles.NOT_FOUND).isEqualTo(TITLES.get("notFound").asString());
  }

  @Test
  void builtTitlesMatchTheSharedFixture() { // 007 R2.1
    assertThat(PageTitles.story(TITLES.get("story").get("input").asString()))
        .isEqualTo(TITLES.get("story").get("title").asString());
    assertThat(PageTitles.section(TITLES.get("section").get("input").asString()))
        .isEqualTo(TITLES.get("section").get("title").asString());
  }
}
