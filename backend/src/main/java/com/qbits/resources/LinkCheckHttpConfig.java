package com.qbits.resources;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the host APIs used by link checks (spec 005 R4). Kept apart from the feed client,
 * which never follows redirects: the GitHub API answers a renamed repository with a 301, and spec
 * 005 follows it. Timeout and User-Agent come from {@link LinkCheckHttpProperties}.
 */
@Configuration
class LinkCheckHttpConfig {

  @Bean
  RestClient linkCheckRestClient(LinkCheckHttpProperties props) {
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
        .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent())
        .build();
  }
}
