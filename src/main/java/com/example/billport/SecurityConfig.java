package com.example.billport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// No login in this example (see the README). CSRF protection stays
		// on for every form, on your host and on customer domains.
		http.authorizeHttpRequests((auth) -> auth.anyRequest().permitAll())
			// Webhooks are authenticated by their token, not a session.
			.csrf((csrf) -> csrf.ignoringRequestMatchers("/webhooks/**"));
		return http.build();
	}

}
