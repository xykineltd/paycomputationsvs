package com.xykine.computation.config;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Value("${admin.service.url}")
    private String adminServiceUrl;

    @Value("${admin.service.maxBufferSize}")
    private Integer maxBufferSize;


    @Bean
    WebClient webClient(WebClient.Builder webClientBuilder) {
        var mediaType = MediaType.APPLICATION_JSON_VALUE;

        return webClientBuilder
                .baseUrl(adminServiceUrl)
                .exchangeStrategies(ExchangeStrategies
                        .builder()
                        .codecs(codecs -> codecs
                                .defaultCodecs()
                                .maxInMemorySize(maxBufferSize * 1024))
                        .build())
                .defaultHeader("Accept", mediaType)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

