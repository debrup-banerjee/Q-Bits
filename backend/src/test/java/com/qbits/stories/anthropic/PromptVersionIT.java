package com.qbits.stories.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.qbits.IntegrationTest;
import com.qbits.stories.StoryWriterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PromptVersionIT extends IntegrationTest {

  private static final String NAMES_RULE =
      "Write every product, model and company name exactly as the input writes it";

  @Autowired StoryWriterProperties props;

  @Test
  void configuredPromptKeepsProductAndModelNamesAsWritten() { // 002 R6.2
    assertThat(props.promptVersion()).isEqualTo("v3");
    assertThat(StoryRequests.loadPrompt(props.promptVersion())).contains(NAMES_RULE);
  }

  @Test
  void earlierPromptVersionIsKeptUnchanged() { // 002 R6.2, R10.1
    String v1 = StoryRequests.loadPrompt("v1");
    String v2 = StoryRequests.loadPrompt("v2");
    assertThat(v1).doesNotContain(NAMES_RULE);
    String v2WithoutNamesRule =
        v2.replaceAll("(?m)^ {3}Write every product[\\s\\S]*?different name\\.\\n", "");
    assertThat(v2WithoutNamesRule).isEqualTo(v1);
  }
}
