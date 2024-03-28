package com.xykine.computation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    WebClient webClient(WebClient.Builder webClientBuilder) {
        var mediaType = MediaType.APPLICATION_JSON_VALUE;
        return webClientBuilder
//                .baseUrl("http://xykinehrs.com/")
//                .baseUrl("http://localhost:9001/")
                .baseUrl("http://localhost:8081/")
                .defaultHeader("Accept", mediaType)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

