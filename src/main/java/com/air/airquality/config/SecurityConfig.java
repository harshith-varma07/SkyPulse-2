package com.air.airquality.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity(debug = false) // Disable debug for cleaner logs
public class SecurityConfig {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                // Public API endpoints - no authentication required for development
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/aqi/**").permitAll() // Allow all AQI endpoints for development
                .antMatchers("/api/export/**").permitAll()
                .antMatchers("/api/alerts/**").permitAll() // Allow alerts for testing
                .antMatchers("/api/users/**").permitAll() // Allow user endpoints for testing
                .antMatchers("/h2-console/**").permitAll() // H2 console access
                .antMatchers("/actuator/health").permitAll() // Health check
                .anyRequest().permitAll() // Allow all requests for development
            )
            .headers(headers -> headers.frameOptions().sameOrigin()) // Allow H2 console frames
            .httpBasic(withDefaults());
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}