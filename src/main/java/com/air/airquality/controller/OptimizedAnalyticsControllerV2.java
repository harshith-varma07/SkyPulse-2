package com.air.airquality.controller;

import com.air.airquality.services.OptimizedAnalyticsServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class OptimizedAnalyticsControllerV2 {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedAnalyticsControllerV2.class);
    
    @Autowired
    private OptimizedAnalyticsServiceV2 analyticsService;
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAnalyticsStats(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            // Check authentication for premium features
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Authentication required for analytics",
                    "requiresAuth", true
                ));
            }
            
            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30); // Default to last 30 days
            
            // Validate date range (max 18 months as per requirements)
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 548) { // 18 months
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Date range cannot exceed 18 months (548 days)",
                    "maxDaysAllowed", 548,
                    "requestedDays", daysBetween
                ));
            }
            
            Map<String, Object> stats = analyticsService.getAnalyticsStats(city, startDate, endDate);
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error generating analytics stats for city {}: {}", city, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to generate analytics statistics",
                "error", e.getMessage()
            ));
        }
    }
    
    @GetMapping("/trend/{city}")
    public ResponseEntity<Map<String, Object>> getTrendChart(
            @PathVariable String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Authentication required for chart data",
                    "requiresAuth", true
                ));
            }
            
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(7); // Default to last 7 days for charts
            
            Map<String, Object> chartData = analyticsService.getTrendChartData(city, startDate, endDate);
            return ResponseEntity.ok(chartData);
            
        } catch (Exception e) {
            logger.error("Error generating trend chart for city {}: {}", city, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to generate trend chart",
                "error", e.getMessage()
            ));
        }
    }
    
    @GetMapping("/health-check")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "service", "OptimizedAnalyticsServiceV2",
            "status", "healthy",
            "features", List.of("statistics", "trends", "pollutants", "categories", "comparison"),
            "timestamp", LocalDateTime.now()
        ));
    }
}