package com.qbits.ingestion;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client used for feeds and robots.txt: timeouts, redirects and an honest User-Agent. */
@Configuration
class HttpConfig {

  @Bean
  RestClient feedRestClient(IngestionProperties props) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(props.timeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
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
