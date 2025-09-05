package com.air.airquality.config;

import com.air.airquality.services.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        final Long userId;

        // Skip JWT validation for public endpoints
        String requestPath = request.getRequestURI();
        if (isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtService.getUsernameFromToken(jwt);
            userId = jwtService.getUserIdFromToken(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Create authentication token
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    username, null, new ArrayList<>()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Store userId in request attributes for controllers to access
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
                
                SecurityContextHolder.getContext().setAuthentication(authToken);
                
                logger.debug("JWT authentication successful for user: {} (ID: {})", username, userId);
            }
        } catch (Exception e) {
            logger.warn("JWT token validation failed: {}", e.getMessage());
            // Continue without authentication - let endpoints handle the 401
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/") ||
               path.startsWith("/api/aqi/current/") ||
               path.startsWith("/api/aqi/cities") ||
               path.startsWith("/api/aqi/search") ||
               path.startsWith("/api/aqi/multiple") ||
               path.startsWith("/api/aqi/cities/add") ||
               path.startsWith("/actuator/health") ||
               path.startsWith("/h2-console/");
    }
}