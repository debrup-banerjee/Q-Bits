package com.qbits.common;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/** UUIDv7 ids: time-ordered, so new rows sort after old ones. */
public final class Ids {

  private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

  private Ids() {}

  public static UUID newId() {
    return GENERATOR.generate();
  }
}
