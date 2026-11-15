package com.qbits.sources;

import com.qbits.config.QBitsProperties;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SourcesConfig {

  private static final Logger log = LoggerFactory.getLogger(SourcesConfig.class);

  /**
   * The minimum interval is read from the ingestion setting directly, so the sources feature does
   * not depend on the ingestion feature's classes and both use the same value (R2.1).
   */
  @Bean
  SourceRegistry sourceRegistry(
      QBitsProperties properties, @Value("${qbits.ingestion.min-interval}") Duration minInterval) {
    Path file = Path.of(properties.sourcesFile());
    SourceRegistry registry = SourceRegistry.load(file, minInterval);
    log.info(
        "sources loaded file={} total={} enabled={}",
        file,
        registry.all().size(),
        registry.enabled().size());
    return registry;
  }
}
