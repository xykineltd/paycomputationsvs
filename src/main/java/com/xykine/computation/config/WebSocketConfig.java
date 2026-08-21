package com.xykine.computation.config;

import org.springframework.context.annotation.Configuration;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // endpoint React will connect to
        registry.addEndpoint("/ws-job")
                .setAllowedOrigins("*")
                .withSockJS(); // fallback support
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // prefix for messages FROM server to client
        registry.enableSimpleBroker("/topic");
        // prefix for messages FROM client to server
        registry.setApplicationDestinationPrefixes("/compute");
    }
}

