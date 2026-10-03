package com.qbits.sources;

import com.qbits.sources.domain.Source;
import com.qbits.sources.domain.SourcesFileParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The approved sources. Loaded once at startup; an invalid file stops the app (R1.2, R1.4). */
public class SourceRegistry {

  private final List<Source> sources;

  public SourceRegistry(List<Source> sources) {
    this.sources = List.copyOf(sources);
  }

  /** Reads and validates the sources file. */
  public static SourceRegistry load(Path file) {
    String text;
    try {
      text = Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read sources file " + file.toAbsolutePath(), e);
    }
    return new SourceRegistry(new SourcesFileParser().parse(text));
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
