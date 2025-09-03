package com.air.airquality.controller;

import com.air.airquality.model.UserAlert;
import com.air.airquality.services.AlertService;
import com.air.airquality.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);
    
    @Autowired
    private AlertService alertService;
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/create")
    public ResponseEntity<?> createAlert(
            @RequestParam String city,
            @RequestParam Integer threshold,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Authentication required to create alerts");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            // Update user's city and alert threshold
            var user = userService.getUserById(Long.parseLong(userId));
            if (user != null) {
                user.setCity(city);
                user.setAlertThreshold(threshold);
                userService.updateUser(Long.parseLong(userId), user);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Alert settings updated successfully");
            response.put("city", city);
            response.put("threshold", threshold);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating alert: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error creating alert");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @GetMapping("/history")
    public ResponseEntity<?> getAlertHistory(HttpServletRequest request) {
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            List<UserAlert> alerts = alertService.getUserAlerts(Long.parseLong(userId));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alerts", alerts);
            response.put("count", alerts.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting alert history: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error retrieving alert history");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
