package com.qbits.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SourceIngestorTest {

  @Test
  void errorTextKeepsOnlyClassAndFirstLineSoRowValuesNeverReachLogs() { // 001 R7.2
    var e =
        new DataIntegrityViolationException(
            "PreparedStatementCallback; ERROR: new row for relation \"items\" violates check"
                + " constraint\n  Detail: Failing row contains (secret title, secret excerpt)");

    String error = SourceIngestor.shortError(e);

    assertThat(error)
        .startsWith("DataIntegrityViolationException: PreparedStatementCallback")
        .doesNotContain("secret")
        .doesNotContain("Failing row");
  }

  @Test
  void errorTextIsClippedAndFallsBackToTheClassName() { // 001 R7.2
    assertThat(SourceIngestor.shortError(new IllegalStateException("x".repeat(1000))))
        .hasSizeLessThan(260);
    assertThat(SourceIngestor.shortError(new IllegalStateException()))
        .isEqualTo("IllegalStateException");
  }
}
