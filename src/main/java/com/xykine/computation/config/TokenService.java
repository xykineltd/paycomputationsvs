package com.xykine.computation.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

    public String getToken() {
        // Retrieve the authentication object from the security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if the authentication object is an instance of JwtAuthenticationToken
        if (authentication instanceof JwtAuthenticationToken) {
            // Extract the JWT token value
            String token = ((JwtAuthenticationToken) authentication).getToken().getTokenValue();
            return token;
        }

        // Return null or throw an exception if no token is available
        return null;
    }
}

