package com.qbits.resources.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ResourceLinkNormaliserTest {

  private final ResourceLinkNormaliser normaliser = new ResourceLinkNormaliser();

  @ParameterizedTest(name = "{0}") // 005 R2.2
  @CsvSource(
      delimiter = '|',
      value = {
        "https://github.com/openai/whisper/tree/main/src | CODE | GITHUB | https://github.com/openai/whisper | openai/whisper | Code on GitHub",
        "http://www.github.com/OpenAI/Whisper.git | CODE | GITHUB | https://github.com/OpenAI/Whisper | OpenAI/Whisper | Code on GitHub",
        "https://github.com/openai/whisper/releases/tag/v1?utm_source=x#readme | CODE | GITHUB | https://github.com/openai/whisper | openai/whisper | Code on GitHub",
        "https://gitlab.com/group/sub/project/-/tree/main | CODE | GITLAB | https://gitlab.com/group/sub/project | group/sub/project | Code on GitLab",
        "https://huggingface.co/meta-llama/Llama-4-8B/blob/main/config.json | MODEL | HUGGINGFACE | https://huggingface.co/meta-llama/Llama-4-8B | meta-llama/Llama-4-8B | Model on Hugging Face",
        "https://huggingface.co/datasets/allenai/c4 | DATASET | HUGGINGFACE | https://huggingface.co/datasets/allenai/c4 | allenai/c4 | Dataset on Hugging Face",
        "https://huggingface.co/spaces/org/demo | CODE | HUGGINGFACE | https://huggingface.co/spaces/org/demo | org/demo | Demo on Hugging Face",
        "https://huggingface.co/papers/2410.01234 | PAPER | ARXIV | https://arxiv.org/abs/2410.01234 | 2410.01234 | Paper on arXiv",
        "https://arxiv.org/pdf/2410.01234v2 | PAPER | ARXIV | https://arxiv.org/abs/2410.01234 | 2410.01234 | Paper on arXiv",
        "https://arxiv.org/pdf/2410.01234v2.pdf | PAPER | ARXIV | https://arxiv.org/abs/2410.01234 | 2410.01234 | Paper on arXiv",
        "https://www.arxiv.org/html/2410.01234v1 | PAPER | ARXIV | https://arxiv.org/abs/2410.01234 | 2410.01234 | Paper on arXiv",
        "https://export.arxiv.org/abs/2410.12345 | PAPER | ARXIV | https://arxiv.org/abs/2410.12345 | 2410.12345 | Paper on arXiv",
        "https://arxiv.org/abs/cs/0112017v1 | PAPER | ARXIV | https://arxiv.org/abs/cs/0112017 | cs/0112017 | Paper on arXiv",
      })
  void normalisesAllowlistedProjectLinks(
      String input, ResourceType type, ResourceHost host, String url, String name, String label) {
    Candidate c = normaliser.normalise(input).orElseThrow();

    assertThat(c.type()).isEqualTo(type);
    assertThat(c.host()).isEqualTo(host);
    assertThat(c.url()).isEqualTo(url);
    assertThat(c.name()).isEqualTo(name);
    assertThat(c.label()).isEqualTo(label);
  }

  @ParameterizedTest // 005 R2.1, R2.3
  @ValueSource(
      strings = {
        "https://github.com/openai",
        "https://github.com/features/copilot",
        "https://github.com/topics/llm",
        "https://github.com/sponsors/someone",
        "https://github.com/login",
        "https://gist.github.com/someone/abc123",
        "https://huggingface.co/blog/some-post",
        "https://huggingface.co/docs/transformers",
        "https://huggingface.co/models?search=llama",
        "https://huggingface.co/meta-llama",
        "https://huggingface.co/datasets",
        "https://arxiv.org/list/cs.AI/recent",
        "https://arxiv.org/abs/not-an-id",
        "https://gitlab.com/explore/projects",
        "https://example.com/openai/whisper",
        "https://twitter.com/openai",
        "ftp://github.com/openai/whisper",
        "not a url",
        "",
      })
  void dropsEverythingElse(String input) {
    assertThat(normaliser.normalise(input)).isEmpty();
  }
}
