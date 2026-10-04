package com.qbits.ingestion;

import com.qbits.ingestion.domain.SourceHealth;
import com.qbits.ingestion.domain.SourceState;
import com.qbits.ingestion.persistence.SourceStateRepository;
import com.qbits.sources.SourceRegistry;
import com.qbits.sources.domain.Source;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Per-source status for {@code /actuator/health} under "sources" (spec 001 R7.5). The app stays UP
 * while some feeds fail; details show which ones need attention.
 */
@Component("sources")
public class SourcesHealthIndicator implements HealthIndicator {

  private final SourceRegistry registry;
  private final SourceStateRepository states;

  public SourcesHealthIndicator(SourceRegistry registry, SourceStateRepository states) {
    this.registry = registry;
    this.states = states;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    long degraded = 0;
    for (Source source : registry.enabled()) {
      SourceState state = states.find(source.id()).orElse(SourceState.initial(source.id()));
      if (state.health() == SourceHealth.DEGRADED) {
        degraded++;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put(
          "status", state.lastFetchedAt() == null ? "NOT_YET_FETCHED" : state.health().name());
      entry.put(
          "lastSuccessAt", state.lastSuccessAt() == null ? null : state.lastSuccessAt().toString());
      entry.put("consecutiveFailures", state.consecutiveFailures());
      details.put(source.id(), entry);
    }
    return Health.up()
        .withDetail("enabled", registry.enabled().size())
        .withDetail("degraded", degraded)
        .withDetail("sources", details)
        .build();
  }
}
