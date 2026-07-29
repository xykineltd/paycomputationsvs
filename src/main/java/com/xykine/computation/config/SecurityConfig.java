package com.xykine.computation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Profile("!test")
@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
		boolean isProd = environment.acceptsProfiles(Profiles.of("prod", "production"));

		http
				.cors()
				.and()
				.authorizeHttpRequests(authz -> {
					authz.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
					if (!isProd) {
						authz.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
					}
					authz.requestMatchers("/actuator/**").hasAnyAuthority("ROLE_PAYROLL_ADMIN");
					authz.requestMatchers("/**").hasAnyAuthority(authorities);
				})
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt
								.jwtAuthenticationConverter(new CustomJwtAuthenticationConverter())
						)
				)
				.csrf().disable();

		return http.build();
	}

}
