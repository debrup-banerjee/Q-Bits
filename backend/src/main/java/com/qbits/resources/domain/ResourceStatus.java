package com.qbits.resources.domain;

/** Check state of a candidate link (spec 005 R4). */
public enum ResourceStatus {
  PENDING,
  VERIFIED,
  NOT_FOUND,
  CHECK_FAILED
}
