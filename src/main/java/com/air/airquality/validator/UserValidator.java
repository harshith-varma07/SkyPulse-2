package com.air.airquality.validator;

import com.air.airquality.dto.UserRegistrationRequest;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class UserValidator {
    
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("^[+]?[1-9]\\d{1,14}$");
    
    public void validateRegistration(UserRegistrationRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().length() < 3) {
            throw new RuntimeException("Username must be at least 3 characters long");
        }
        
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters long");
        }
        
        if (request.getEmail() == null || !EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new RuntimeException("Invalid email address");
        }
        
        if (request.getPhoneNumber() != null && 
            !request.getPhoneNumber().isEmpty() && 
            !PHONE_PATTERN.matcher(request.getPhoneNumber()).matches()) {
            throw new RuntimeException("Invalid phone number format");
        }
        
        if (request.getCity() == null || request.getCity().trim().isEmpty()) {
            throw new RuntimeException("City is required");
        }
        
        if (request.getAlertThreshold() != null && 
            (request.getAlertThreshold() < 0 || request.getAlertThreshold() > 500)) {
            throw new RuntimeException("Alert threshold must be between 0 and 500");
        }
    }
}