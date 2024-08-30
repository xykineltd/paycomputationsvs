package com.xykine.computation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;


@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public JwtTokenFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Jwt jwt = jwtDecoder.decode(token);

            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");

            // Create a UserDetails object with the user's information
            UserDetails userDetails = User.withUsername(name)
                    .authorities(Collections.emptyList()) // You can set the user's roles/authorities here
                    .password("") // Password is not needed here
                    .build();

            // Create a JwtAuthenticationToken with the userDetails and the Jwt token
//            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, userDetails.getAuthorities(), userDetails);
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, null, email);


            // Set the authentication in the SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);


            System.out.println("authentication==>" + SecurityContextHolder.getContext().getAuthentication().getName());
        }
        // Continue the filter chain
        filterChain.doFilter(request, response);
    }

//    @Override
//    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
//            throws IOException, ServletException {
//        String authHeader = request.getHeader("Authorization");
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            String token = authHeader.substring(7);
//            Jwt jwt = jwtDecoder.decode(token);
//
//            JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
//            String email = jwt.getClaimAsString("email");
//            String name = jwt.getClaimAsString("name");
//            System.out.println("authentication==>" + email + ", ===>"+ name);
//            SecurityContextHolder.getContext().setAuthentication(authentication);
//        }
//        // Continue the filter chain
//        filterChain.doFilter(request, response);
//    }
}

