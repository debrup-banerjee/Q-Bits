package com.qbits.api;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS: only the configured web origins, GET only (conventions). */
@Configuration
@EnableConfigurationProperties(WebConfig.WebProperties.class)
class WebConfig implements WebMvcConfigurer {

  @ConfigurationProperties(prefix = "qbits.web")
  record WebProperties(List<String> allowedOrigins) {}

  private final WebProperties props;

  WebConfig(WebProperties props) {
    this.props = props;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
        .allowedMethods("GET")
        .exposedHeaders(CatalogController.DATA_AS_OF)
        .allowCredentials(false);
  }
}
