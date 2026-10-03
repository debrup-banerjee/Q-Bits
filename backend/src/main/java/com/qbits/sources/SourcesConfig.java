package com.qbits.sources;

import com.qbits.config.QBitsProperties;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SourcesConfig {

  private static final Logger log = LoggerFactory.getLogger(SourcesConfig.class);

  @Bean
  SourceRegistry sourceRegistry(QBitsProperties properties) {
    Path file = Path.of(properties.sourcesFile());
    SourceRegistry registry = SourceRegistry.load(file);
    log.info(
        "sources loaded file={} total={} enabled={}",
        file,
        registry.all().size(),
        registry.enabled().size());
    return registry;
  }
}
