package com.qbits.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

  @Test
  void roundTrips() {
    Cursor c = new Cursor(Instant.parse("2026-10-03T04:10:00.123456Z"), UUID.randomUUID());

    String text = CursorCodec.encode(c);

    assertThat(text).doesNotContain("=", "+", "/");
    assertThat(CursorCodec.decode(text)).contains(c);
  }

  @Test
  void rejectsGarbage() {
    assertThat(CursorCodec.decode("not-a-cursor")).isEmpty();
    assertThat(CursorCodec.decode("")).isEmpty();
  }
}
