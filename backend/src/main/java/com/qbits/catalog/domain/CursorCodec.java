package com.qbits.catalog.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Opaque, URL-safe cursor strings (spec 003 R2.1). Pure. */
public final class CursorCodec {

  private CursorCodec() {}

  public static String encode(Cursor cursor) {
    String raw = cursor.publishedAt().toString() + "|" + cursor.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Empty if the text is not a cursor we issued. */
  public static Optional<Cursor> decode(String text) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", 2);
      if (parts.length != 2) {
        return Optional.empty();
      }
      return Optional.of(new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1])));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }
}
