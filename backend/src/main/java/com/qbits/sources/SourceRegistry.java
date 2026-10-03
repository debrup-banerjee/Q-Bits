package com.qbits.sources;

import com.qbits.config.QBitsProperties;
import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourcesFileParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Loads the approved sources at startup. An invalid file stops the application (R1.2, R1.4). */
@Service
public class SourceRegistry {

  private static final Logger log = LoggerFactory.getLogger(SourceRegistry.class);

  private final List<Source> sources;

  public SourceRegistry(QBitsProperties properties) {
    Path file = Path.of(properties.sourcesFile());
    String text;
    try {
      text = Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read sources file " + file.toAbsolutePath(), e);
    }
    this.sources = new SourcesFileParser().parse(text);
    log.info(
        "sources loaded file={} total={} enabled={}",
        file,
        sources.size(),
        sources.stream().filter(Source::enabled).count());
  }

  public List<Source> all() {
    return sources;
  }

  public List<Source> enabled() {
    return sources.stream().filter(Source::enabled).toList();
  }

  public Optional<Source> find(String id) {
    return sources.stream().filter(s -> s.id().equals(id)).findFirst();
  }
}
