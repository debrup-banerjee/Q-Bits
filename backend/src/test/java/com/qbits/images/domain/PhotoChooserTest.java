package com.qbits.images.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PhotoChooserTest {

  private static PhotoCandidate photo(String id, String url, String photographer) {
    return new PhotoCandidate(
        id, url, "https://www.pexels.com/photo/" + id, "a chip", photographer, null, "#112233");
  }

  @Test
  void skipsRecentlyUsedUnsafeAndUncreditedPhotos() { // 009 R2.4
    List<PhotoCandidate> results =
        List.of(
            photo("used", "https://images.pexels.com/a", "Ann"),
            photo("http", "http://images.pexels.com/b", "Bo"),
            photo("anon", "https://images.pexels.com/c", " "),
            photo("good", "https://images.pexels.com/d", "Dee"));

    assertThat(PhotoChooser.choose(results, Set.of("used")))
        .map(PhotoCandidate::id)
        .contains("good");
  }

  @Test
  void emptyWhenNothingFits() { // 009 R2.4
    assertThat(PhotoChooser.choose(List.of(), Set.of())).isEmpty();
  }
}
