package com.qbits.resources;

import com.qbits.resources.domain.Candidate;

/** Confirms a link exists. {@link HostApis} is the real one; tests use a scripted fake. */
public interface LinkChecker {
  CheckResult check(Candidate candidate);
}
