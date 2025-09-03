package com.air.airquality.controller;

import com.air.airquality.services.AqiService;
import com.air.airquality.services.OpenAQService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    
    @Autowired
    private AqiService aqiService;
    
    @Autowired
    private OpenAQService openAQService;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Basic health check
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now());
            health.put("service", "AirSight Air Quality Monitoring");
            health.put("version", "1.0.0");
            
            // Check database connectivity
            try {
                long recordCount = aqiService.getTotalRecords();
                health.put("database", "UP");
                health.put("totalRecords", recordCount);
            } catch (Exception e) {
                health.put("database", "DOWN");
                health.put("databaseError", e.getMessage());
            }
            
            // Check external API connectivity
            try {
                boolean apiConnected = openAQService.isApiHealthy();
                health.put("externalAPI", apiConnected ? "UP" : "DOWN");
            } catch (Exception e) {
                health.put("externalAPI", "DOWN");
                health.put("apiError", e.getMessage());
            }
            
            // Check if any critical errors exist
            boolean isHealthy = "UP".equals(health.get("database")) || 
                              "UP".equals(health.get("externalAPI"));
            
            if (!isHealthy) {
                return ResponseEntity.status(503).body(health);
            }
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }
    
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> ready = new HashMap<>();
        
        try {
            // More thorough readiness check
            boolean databaseReady = aqiService.isDatabaseReady();
            boolean apiReady = openAQService.isApiHealthy();
            
            ready.put("status", (databaseReady || apiReady) ? "READY" : "NOT_READY");
            ready.put("timestamp", LocalDateTime.now());
            ready.put("database", databaseReady ? "READY" : "NOT_READY");
            ready.put("externalAPI", apiReady ? "READY" : "NOT_READY");
            
            if (databaseReady || apiReady) {
                return ResponseEntity.ok(ready);
            } else {
                return ResponseEntity.status(503).body(ready);
            }
            
        } catch (Exception e) {
            ready.put("status", "ERROR");
            ready.put("error", e.getMessage());
            return ResponseEntity.status(503).body(ready);
        }
    }
}
