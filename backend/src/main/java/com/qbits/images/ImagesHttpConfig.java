package com.qbits.images;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client for the photo library API (spec 009). Same honest User-Agent as feed fetching. */
@Configuration
class ImagesHttpConfig {

  @Bean
  RestClient imagesRestClient(ImagesProperties props) {
    ImagesProperties.Pexels u = props.pexels();
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(u.timeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(u.timeout());
    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.USER_AGENT, u.userAgent())
        .build();
  }
}
