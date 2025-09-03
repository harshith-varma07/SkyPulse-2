package com.air.airquality.controller;

import com.air.airquality.services.HistoricalDataSeederService;
import com.air.airquality.services.AqiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Autowired
    private HistoricalDataSeederService seederService;
    
    @Autowired
    private AqiService aqiService;
    
    @PostMapping("/seed-historical-data")
    public ResponseEntity<?> seedHistoricalData(@RequestParam(defaultValue = "3") int years) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Check current record count
            long existingRecords = aqiService.getTotalRecords();
            
            if (existingRecords > 50000) {
                response.put("success", false);
                response.put("message", "Historical data already exists (" + existingRecords + " records). Use force=true to regenerate.");
                return ResponseEntity.ok(response);
            }
            
            // Start seeding in a separate thread to avoid timeout
            Thread seedingThread = new Thread(() -> {
                try {
                    LocalDateTime startDate = LocalDateTime.now().minus(years, ChronoUnit.YEARS);
                    seederService.generateHistoricalData(startDate, LocalDateTime.now());
                } catch (Exception e) {
                    // Log error but don't fail the response
                    e.printStackTrace();
                }
            });
            
            seedingThread.start();
            
            response.put("success", true);
            response.put("message", "Historical data seeding started for past " + years + " years. This may take a few minutes.");
            response.put("existingRecords", existingRecords);
            response.put("estimatedNewRecords", years * 10 * 365 * 4); // Rough estimate
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error starting data seeding: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/force-seed-historical-data")
    public ResponseEntity<?> forceSeedHistoricalData(@RequestParam(defaultValue = "3") int years) {
        try {
            // Force regeneration by clearing existing data first (optional)
            Thread seedingThread = new Thread(() -> {
                try {
                    LocalDateTime startDate = LocalDateTime.now().minus(years, ChronoUnit.YEARS);
                    seederService.generateHistoricalData(startDate, LocalDateTime.now());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            seedingThread.start();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Historical data force seeding started for past " + years + " years.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error starting force data seeding: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/database-status")
    public ResponseEntity<?> getDatabaseStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            
            long totalRecords = aqiService.getTotalRecords();
            boolean isReady = aqiService.isDatabaseReady();
            
            response.put("success", true);
            response.put("totalRecords", totalRecords);
            response.put("isDatabaseReady", isReady);
            response.put("availableCities", aqiService.getAvailableCities());
            
            if (totalRecords < 1000) {
                response.put("recommendation", "Consider seeding historical data for better analytics experience");
                response.put("seedEndpoint", "/api/admin/seed-historical-data");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting database status: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @DeleteMapping("/cleanup-old-data")
    public ResponseEntity<?> cleanupOldData(@RequestParam(defaultValue = "90") int daysToKeep) {
        try {
            long recordsBefore = aqiService.getTotalRecords();
            aqiService.cleanupOldData(daysToKeep);
            long recordsAfter = aqiService.getTotalRecords();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("recordsBefore", recordsBefore);
            response.put("recordsAfter", recordsAfter);
            response.put("recordsDeleted", recordsBefore - recordsAfter);
            response.put("message", "Cleanup completed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error during cleanup: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
