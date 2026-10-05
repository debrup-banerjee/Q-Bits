package com.qbits.pages.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTest {

  @Test
  void escapesMarkupCharactersInText() { // 007 R2.7
    assertThat(Html.text("<b>\"Tom\" & 'Jerry'</b>"))
        .isEqualTo("&lt;b&gt;&quot;Tom&quot; &amp; &#39;Jerry&#39;&lt;/b&gt;");
  }

  @Test
  void leavesPlainTextAlone() {
    assertThat(Html.text("India AI — 72 hours")).isEqualTo("India AI — 72 hours");
  }

  @Test
  void jsonInScriptCannotCloseTheElement() { // 007 R2.7
    String json = "{\"h\":\"</script><script>alert(1)</script><!--\"}";

    String safe = Html.scriptJson(json);

    assertThat(safe).doesNotContain("</script", "<!--", "<");
    assertThat(safe).contains("\\u003c/script>");
  }

  @Test
  void escapesLineSeparatorsInJson() { // 007 R2.7
    assertThat(Html.scriptJson("\"a\u2028b\u2029c\"")).isEqualTo("\"a\\u2028b\\u2029c\"");
  }
}
