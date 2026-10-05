package com.qbits.relevance;

import com.qbits.relevance.domain.RelevancePreFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RelevanceConfig {

  @Bean
  RelevancePreFilter relevancePreFilter(RelevanceProperties properties) {
    return new RelevancePreFilter(properties.keywords(), properties.threshold());
  }
}
