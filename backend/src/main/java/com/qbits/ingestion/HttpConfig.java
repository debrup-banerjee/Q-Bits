package com.qbits.ingestion;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client used for feeds and robots.txt: timeouts and an honest User-Agent (spec 001 R2.4).
 *
 * <p>Redirects are never followed, so a fetch only ever reaches a registered feed URL or its host's
 * robots.txt (R1.3). A redirect comes back as a 3xx response and is recorded as a failure.
 */
@Configuration
class HttpConfig {

  @Bean
  RestClient feedRestClient(IngestionProperties props) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(props.timeout())
            .followRedirects(HttpClient.Redirect.NEVER) // R1.3
            .proxy(ProxySelector.getDefault())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(props.timeout());
    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent()) // R2.4
        .build();
  }
}
