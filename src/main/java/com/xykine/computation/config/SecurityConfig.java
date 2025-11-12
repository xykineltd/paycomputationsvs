package com.xykine.computation.config;//package com.xykine.adminservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Profile("!test")
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	//	final String[] authorities = {"ROLE_ADMIN", "ROLE_EMPLOYEE"};
	final String[] authorities = {
			"ROLE_PAYROLL_ADMIN",
			"ROLE_PAYROLL_VENDOR",
			"ROLE_PAYROLL_MANAGER",
			"ROLE_PAYROLL_OFFICER",
			"ROLE_PAYROLL_STAFF",
			"ROLE_PAYROLL_HR",
			"ROLE_EMPLOYEE",
			"ROLE_PAYROLL_PREP_PAYMENT",
			"ROLE_PAYROLL_DISBURSE",
			"EMPLOYEE",
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors()
				.and()
				.authorizeHttpRequests(authz -> authz
						.requestMatchers("/actuator/**").permitAll()
						.requestMatchers("/api/users/create").permitAll()
						.requestMatchers("/api/monnify/webhooks/disbursement").permitAll()
						.requestMatchers("/api/iam/realms").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/actuator/prometheus").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						//TODO add more roles
						.requestMatchers("/**").hasAnyAuthority(authorities)
				)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt
								.jwtAuthenticationConverter(new CustomJwtAuthenticationConverter())
						)
				)
				.csrf().disable();

		return http.build();
	}

}
