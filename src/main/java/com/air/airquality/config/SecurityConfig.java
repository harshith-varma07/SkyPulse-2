package com.air.airquality.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity(debug = false)
public class SecurityConfig {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Public API endpoints - no authentication required
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/aqi/current/**").permitAll()
                .antMatchers("/api/aqi/cities").permitAll()
                .antMatchers("/api/aqi/search").permitAll()
                .antMatchers("/api/aqi/multiple").permitAll()
                .antMatchers("/api/aqi/cities/add").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                
                // Protected endpoints - require JWT authentication
                .antMatchers("/api/aqi/historical/**").authenticated()
                .antMatchers("/api/export/**").authenticated()
                .antMatchers("/api/alerts/**").authenticated()
                .antMatchers("/api/users/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions().sameOrigin()) // Allow H2 console frames
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}