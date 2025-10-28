package com.xykine.computation.config;

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

    @Value("${workflow.service.url}")
    private String workFlowServiceUrl;

    @Value("${admin.service.maxBufferSize}")
    private Integer maxBufferSize;

    private String mediaType = MediaType.APPLICATION_JSON_VALUE;

    @Bean
    WebClient adminWebClient(WebClient.Builder webClientBuilder) {
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

    @Bean
    WebClient workflowWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(workFlowServiceUrl)
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

