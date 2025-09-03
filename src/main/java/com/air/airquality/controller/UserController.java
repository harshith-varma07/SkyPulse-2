package com.air.airquality.controller;

import com.air.airquality.model.User;
import com.air.airquality.model.UserAlert;
import com.air.airquality.services.UserService;
import com.air.airquality.services.AlertService;
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
@RequestMapping("/api/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AlertService alertService;
    
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(HttpServletRequest request) {
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            User user = userService.getUserById(Long.parseLong(userId));
            if (user == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", user);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting user profile: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error retrieving user profile");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody User updatedUser, HttpServletRequest request) {
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            User user = userService.updateUser(Long.parseLong(userId), updatedUser);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile updated successfully");
            response.put("user", user);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error updating user profile: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating profile");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @GetMapping("/alerts")
    public ResponseEntity<?> getUserAlerts(HttpServletRequest request) {
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
            logger.error("Error getting user alerts: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error retrieving alerts");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @DeleteMapping("/alerts/{alertId}")
    public ResponseEntity<?> deleteUserAlert(@PathVariable Long alertId, HttpServletRequest request) {
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            alertService.deleteUserAlert(alertId, Long.parseLong(userId));
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Alert deleted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error deleting user alert: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting alert");
            response.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
