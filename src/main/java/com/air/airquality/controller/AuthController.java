package com.air.airquality.controller;

import com.air.airquality.dto.LoginRequest;
import com.air.airquality.dto.UserRegistrationRequest;
import com.air.airquality.model.User;
import com.air.airquality.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationRequest request) {
        try {
            User user = userService.registerUser(request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully");
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        try {
            User user = userService.authenticateUser(request.getUsername(), request.getPassword());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("city", user.getCity());
            response.put("alertThreshold", user.getAlertThreshold());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
    
    @PutMapping("/update/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable Long userId, @RequestBody User updatedUser) {
        try {
            User user = userService.updateUser(userId, updatedUser);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User updated successfully");
            response.put("user", user);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}