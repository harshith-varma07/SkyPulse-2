package com.air.airquality.controller;

import com.air.airquality.services.DeploymentHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Deployment and monitoring endpoints for production readiness
 */
@RestController
@RequestMapping("/api/system")
public class DeploymentController {
    
    @Autowired
    private DeploymentHealthService healthService;
    
    /**
     * Comprehensive system status for monitoring dashboards
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = healthService.getSystemStatus();
        return ResponseEntity.ok(status);
    }
    
    /**
     * Simple readiness probe for Kubernetes/Docker
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readinessProbe() {
        return ResponseEntity.ok(Map.of(
            "status", "ready",
            "timestamp", LocalDateTime.now(),
            "service", "SkyPulse Air Quality Monitor"
        ));
    }
    
    /**
     * Simple liveness probe for Kubernetes/Docker
     */
    @GetMapping("/alive")
    public ResponseEntity<Map<String, Object>> livenessProbe() {
        return ResponseEntity.ok(Map.of(
            "status", "alive",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    /**
     * Application information for deployment verification
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getApplicationInfo() {
        return ResponseEntity.ok(Map.of(
            "application", "SkyPulse Air Quality Monitoring",
            "version", "1.0.0",
            "buildTime", "2025-09-03T11:00:00Z",
            "profile", getActiveProfile(),
            "features", List.of(
                "Real-time AQI monitoring",
                "18-month historical data",
                "Advanced analytics",
                "SMS alerts",
                "Multi-city support",
                "Optimized performance"
            ),
            "endpoints", Map.of(
                "aqi", "/api/aqi/*",
                "analytics", "/api/analytics/*",
                "health", "/actuator/health",
                "metrics", "/actuator/metrics"
            )
        ));
    }
    
    private String getActiveProfile() {
        String profiles = System.getProperty("spring.profiles.active");
        return profiles != null ? profiles : "default";
    }
}