package com.air.airquality.controller;

import com.air.airquality.model.User;
import com.air.airquality.model.UserAlert;
import com.air.airquality.dto.CredentialChangeRequest;
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

import com.air.airquality.services.JwtService;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private JwtService jwtService;
    
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(HttpServletRequest request) {
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

            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "User not found"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting user profile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error retrieving user profile", "error", e.getMessage()));
        }
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody User updatedUser, HttpServletRequest request) {
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

            User user = userService.updateUser(userId, updatedUser);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Profile updated successfully");
            response.put("user", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating user profile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Error updating profile", "error", e.getMessage()));
        }
    }
    
    @PutMapping("/credentials")
    public ResponseEntity<?> updateUserCredentials(@RequestBody CredentialChangeRequest request, HttpServletRequest httpRequest) {
        try {
            String authHeader = httpRequest.getHeader("Authorization");
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

            User user = userService.updateUserCredentials(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Credentials updated successfully");
            response.put("user", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating user credentials: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    @GetMapping("/alerts")
    public ResponseEntity<?> getUserAlerts(HttpServletRequest request) {
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
            logger.error("Error getting user alerts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error retrieving alerts", "error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/alerts/{alertId}")
    public ResponseEntity<?> deleteUserAlert(@PathVariable Long alertId, HttpServletRequest request) {
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

            alertService.deleteUserAlert(alertId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Alert deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting user alert: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Error deleting alert", "error", e.getMessage()));
        }
    }
}
