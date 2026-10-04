package com.qbits;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Conventions: "A feature talks to another only through that feature's service classes. No reaching
 * into another feature's persistence." Reads the source files, so it needs no extra library.
 */
class FeatureBoundariesTest {

  private static final Path MAIN = Path.of("src", "main", "java", "com", "qbits");

  @Test
  void storiesNeverImportIngestionPersistence() { // conventions (002 review B4, 006 review D1)
    List<String> offenders =
        filesImporting(MAIN.resolve("stories"), "com.qbits.ingestion.persistence");
    assertThat(offenders).isEmpty();
  }

  @Test
  void theScanFindsTheIngestionFeatureItself() { // guards the test: an empty scan would pass
    assertThat(filesImporting(MAIN.resolve("ingestion"), "com.qbits.ingestion.persistence"))
        .isNotEmpty();
  }

  private static List<String> filesImporting(Path root, String pkg) {
    assertThat(root).isDirectory();
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> imports(p, pkg))
          .map(Path::toString)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean imports(Path file, String pkg) {
    try (Stream<String> lines = Files.lines(file)) {
      return lines.anyMatch(l -> l.contains(pkg + ".")); // imports and fully qualified names
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
