package com.xykine.computation.config;//package com.xykine.adminservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public JwtDecoder jwtDecoder() {
		JwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri("http://backend-keycloak-auth:8080/auth/realms/payroll/protocol/openid-connect/certs").build();
		return token -> {
			System.out.println("token: " + token);
			Jwt jwt = jwtDecoder.decode(token);
			System.out.println("jwt: " + jwt);
			return jwt;
		};
	}


	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenFilter keycloakJwtFilter) throws Exception {
		http
				.authorizeHttpRequests(authorizeRequests ->
						authorizeRequests
								.requestMatchers("/**")
								.hasAuthority("SCOPE_payroll.read")
				)
				.addFilterBefore(keycloakJwtFilter, UsernamePasswordAuthenticationFilter.class)
			   .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);

		return http.build();
	}

//	@Bean
//	public JwtAuthenticationConverter customJwtAuthenticationConverter() {
//		JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
//
//		// Set your custom authorities converter
//		jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new CustomJwtGrantedAuthoritiesConverter());
//
//		// Optionally, set the principal claim name if it's not the default (e.g., "sub")
//		jwtAuthenticationConverter.setPrincipalClaimName("preferred_username");
//
//		return jwtAuthenticationConverter;
//	}

//	@Bean
//	public JwtAuthenticationConverter customJwtAuthenticationConverter() {
//		return new CustomJwtAuthenticationConverter();
//	}

}
