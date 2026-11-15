package com.qbits.stories.persistence;

import java.sql.Date;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Counts calls to the summary service per UTC day (spec 002 R9.1). */
@Repository
public class WriterBudgetRepository {

  private final JdbcClient jdbc;

  public WriterBudgetRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Takes one call from today's budget if any is left. Safe across instances. */
  public boolean tryTake(LocalDate day, int cap) {
    return jdbc.sql(
            """
            insert into writer_budget (day, calls) values (:day, 1)
            on conflict (day) do update set calls = writer_budget.calls + 1
              where writer_budget.calls < :cap
            returning calls
            """)
        .param("day", Date.valueOf(day))
        .param("cap", cap)
        .query(Integer.class)
        .optional()
        .filter(calls -> calls <= cap)
        .isPresent();
  }

  /**
   * Takes up to {@code wanted} calls from today's budget at once; returns how many were granted.
   */
  public synchronized int takeUpTo(LocalDate day, int wanted, int cap) {
    int granted = 0;
    while (granted < wanted && tryTake(day, cap)) {
      granted++;
    }
    return granted;
  }

  public int used(LocalDate day) {
    return jdbc.sql("select coalesce((select calls from writer_budget where day = :day), 0)")
        .param("day", Date.valueOf(day))
        .query(Integer.class)
        .single();
  }
}
