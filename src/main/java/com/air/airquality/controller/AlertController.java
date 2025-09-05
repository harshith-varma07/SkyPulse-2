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

import com.air.airquality.services.JwtService;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);
    
    @Autowired
    private AlertService alertService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;
    
    @PostMapping("/create")
    public ResponseEntity<?> createAlert(
            @RequestParam String city,
            @RequestParam Integer threshold,
            HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required: Bearer token missing"));
            }
            String token = authHeader.substring(7);
            Long userId;
            try {
                userId = jwtService.getUserIdFromToken(token);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid or expired token"));
            }

            // Update user's city and alert threshold
            var user = userService.getUserById(userId);
            if (user != null) {
                user.setCity(city);
                user.setAlertThreshold(threshold);
                userService.updateUser(userId, user);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Alert settings updated successfully");
            response.put("city", city);
            response.put("threshold", threshold);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating alert: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Error creating alert", "error", e.getMessage()));
        }
    }
    
    @GetMapping("/history")
    public ResponseEntity<?> getAlertHistory(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required: Bearer token missing"));
            }
            String token = authHeader.substring(7);
            Long userId;
            try {
                userId = jwtService.getUserIdFromToken(token);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid or expired token"));
            }

            List<UserAlert> alerts = alertService.getUserAlerts(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("alerts", alerts);
            response.put("count", alerts.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting alert history: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error retrieving alert history", "error", e.getMessage()));
        }
    }
}
