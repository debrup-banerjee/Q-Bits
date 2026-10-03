package com.qbits.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qbits.sources.domain.SourceConfigException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRegistryTest {

  @TempDir Path dir;

  @Test
  void refusesToStartOnInvalidFile() throws Exception { // 001 R1.2
    Path file = dir.resolve("sources.yml");
    Files.writeString(file, "sources:\n  - id: bad\n");

    assertThatThrownBy(() -> SourceRegistry.load(file))
        .isInstanceOf(SourceConfigException.class)
        .hasMessageContaining("source 'bad': field 'name' is required");
  }

  @Test
  void exposesOnlyEnabledSources() throws Exception { // 001 R1.1
    Path file = dir.resolve("sources.yml");
    Files.writeString(
        file,
        """
        sources:
          - {id: on-one, name: On, homepage: https://a.example, feedUrl: https://a.example/feed,
             type: rss, aiNative: true, region: global, enabled: true,
             termsUrl: https://a.example/terms, termsReviewedOn: 2026-10-03}
          - {id: off-one, name: Off, homepage: https://b.example, feedUrl: https://b.example/feed,
             type: rss, aiNative: false, region: global, enabled: false}
        """);

    SourceRegistry registry = SourceRegistry.load(file);

    assertThat(registry.all()).hasSize(2);
    assertThat(registry.enabled()).extracting("id").containsExactly("on-one");
    assertThat(registry.find("off-one")).isPresent();
  }
}
