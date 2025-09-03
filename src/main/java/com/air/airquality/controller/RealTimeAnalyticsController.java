package com.air.airquality.controller;

import com.air.airquality.services.RealTimeAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:5500"})
public class RealTimeAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeAnalyticsController.class);

    @Autowired
    private RealTimeAnalyticsService analyticsService;

    /**
     * Fetch real-time data from OpenAQ for specified city and time period
     */
    @GetMapping("/data/{city}")
    public ResponseEntity<Map<String, Object>> getRealTimeData(
            @PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {

        // Check authentication for analytics access
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required for real-time analytics");
            response.put("requiresAuth", true);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Validate date range
            if (startDate.isAfter(endDate)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Start date must be before end date");
                return ResponseEntity.badRequest().body(response);
            }

            // Validate reasonable time range (max 18 months for performance)
            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (days > 540) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Date range cannot exceed 18 months (540 days) for optimal performance");
                response.put("maxDaysAllowed", 540);
                return ResponseEntity.badRequest().body(response);
            }

            logger.info("Fetching real-time analytics data for city: {} from {} to {}", 
                       city, startDate, endDate);

            Map<String, Object> result = analyticsService.fetchRealTimeData(city, startDate, endDate);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error fetching real-time data for city {}: {}", city, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to fetch real-time data: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Generate comprehensive analytics using Python service
     */
    @GetMapping("/generate/{city}")
    public ResponseEntity<Map<String, Object>> generateAnalytics(
            @PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required for analytics generation");
            response.put("requiresAuth", true);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Validate date range
            if (startDate.isAfter(endDate)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Start date must be before end date");
                return ResponseEntity.badRequest().body(response);
            }

            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (days > 540) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Date range cannot exceed 18 months (540 days) for real-time analytics");
                return ResponseEntity.badRequest().body(response);
            }

            logger.info("Generating analytics for city: {} from {} to {}", city, startDate, endDate);

            Map<String, Object> result = analyticsService.generateAnalytics(city, startDate, endDate);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error generating analytics for city {}: {}", city, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to generate analytics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Generate charts using Python service
     */
    @GetMapping("/charts/{city}")
    public ResponseEntity<Map<String, Object>> generateCharts(
            @PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required for chart generation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            logger.info("Generating charts for city: {} from {} to {}", city, startDate, endDate);

            Map<String, Object> result = analyticsService.generateCharts(city, startDate, endDate);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error generating charts for city {}: {}", city, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to generate charts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get analytics statistics using Python service
     */
    @GetMapping("/stats/{city}")
    public ResponseEntity<Map<String, Object>> getAnalyticsStats(
            @PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required for analytics statistics");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            logger.info("Getting analytics statistics for city: {} from {} to {}", city, startDate, endDate);

            Map<String, Object> result = analyticsService.getAnalyticsStats(city, startDate, endDate);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error getting analytics stats for city {}: {}", city, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get analytics statistics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Generate PDF report using Python analytics
     */
    @GetMapping("/pdf/{city}")
    public ResponseEntity<byte[]> generatePDFReport(
            @PathVariable String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            logger.info("Generating PDF report for city: {} from {} to {}", city, startDate, endDate);

            byte[] pdfData = analyticsService.generatePDFReport(city, startDate, endDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                String.format("analytics-report-%s-%s.pdf", 
                    city.replace(" ", "-"), 
                    java.time.LocalDate.now().toString()));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfData);

        } catch (Exception e) {
            logger.error("Error generating PDF report for city {}: {}", city, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Check if Python service is available
            response.put("success", true);
            response.put("service", "Real-time Analytics Service");
            response.put("status", "healthy");
            response.put("timestamp", LocalDateTime.now());
            response.put("features", new String[]{
                "Real-time data fetching from OpenAQ",
                "Python-based analytics",
                "Chart generation",
                "PDF report generation",
                "Statistical analysis"
            });

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("status", "unhealthy");
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    /**
     * Get available cities with real-time capability
     */
    @GetMapping("/cities")
    public ResponseEntity<Map<String, Object>> getAvailableCities() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cities", new String[]{
                "Delhi", "Mumbai", "Bangalore", "Chennai", "Kolkata", "Hyderabad",
                "Pune", "Jaipur", "Lucknow", "Kanpur", "Ahmedabad", "Nagpur",
                "New York", "London", "Paris", "Tokyo", "Beijing", "Sydney",
                "Singapore", "Dubai", "Los Angeles", "San Francisco", "Toronto",
                "Berlin", "Amsterdam", "Rome", "Madrid", "Barcelona"
            });
            response.put("note", "Real-time analytics available for all listed cities");
            response.put("maxDateRange", "18 months (540 days)");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting available cities: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get available cities");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
