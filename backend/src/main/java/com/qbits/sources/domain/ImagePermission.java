package com.qbits.sources.domain;

import java.net.URI;
import java.time.LocalDate;

/**
 * A source's recorded permission to show the image its feed attaches to an entry (spec 009 R3).
 * Written by a person after reading the source's press-kit terms or getting written permission;
 * never inferred. {@code evidence} is where that permission can be checked again, and {@code
 * credit} is the line shown under the image.
 */
public record ImagePermission(Basis basis, URI evidence, LocalDate reviewedOn, String credit) {

  /** Why the images may be shown. */
  public enum Basis {
    /** The organisation's own press kit or newsroom terms allow editorial reuse. */
    PRESS_KIT,
    /** The publisher gave Q-Bits written permission. */
    WRITTEN_PERMISSION
  }
}
