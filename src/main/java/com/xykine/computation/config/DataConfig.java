package com.xykine.computation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.context.SecurityContext;
//import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableMongoAuditing
public class DataConfig {

//    @Bean
//    AuditorAware<String> auditorAware() {
//        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
//                .map(SecurityContext::getAuthentication)
//                .filter(Authentication::isAuthenticated)
//                .map(Authentication::getName);
//    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of("testUser");
    }
}

