package com.qbits.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the system UTC clock so tests can substitute a fixed one. */
@Configuration
class TimeConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
