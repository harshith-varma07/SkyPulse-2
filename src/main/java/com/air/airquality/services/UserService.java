package com.air.airquality.services;

import com.air.airquality.dto.UserRegistrationRequest;
import com.air.airquality.dto.CredentialChangeRequest;
import com.air.airquality.model.User;
import com.air.airquality.repository.UserRepository;
import com.air.airquality.validator.UserValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    // Cache for frequently accessed users
    private final ConcurrentHashMap<Long, User> userCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> usernameCache = new ConcurrentHashMap<>();
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserValidator userValidator;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12); // Stronger encoding
    
    public User registerUser(UserRegistrationRequest request) {
        logger.info("Registering new user: {}", request.getUsername());
        
        // Validate input
        userValidator.validateRegistration(request);
        
        // Check for existing users efficiently
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        // Create and save user
        User user = createUserFromRequest(request);
        User savedUser = userRepository.save(user);
        
        // Cache the new user
        cacheUser(savedUser);
        
        logger.info("Successfully registered user: {}", savedUser.getUsername());
        return savedUser;
    }
    
    private User createUserFromRequest(UserRegistrationRequest request) {
        User user = new User(
            request.getUsername(),
            request.getEmail(),
            passwordEncoder.encode(request.getPassword()),
            request.getPhoneNumber(),
            request.getCity()
        );
        
        if (request.getAlertThreshold() != null) {
            user.setAlertThreshold(request.getAlertThreshold());
        }
        
        return user;
    }
    
    public User authenticateUser(String username, String password) {
        logger.debug("Authenticating user: {}", username);
        
        // Try cache first for O(1) lookup
        User cachedUser = usernameCache.get(username);
        if (cachedUser != null && passwordEncoder.matches(password, cachedUser.getPassword())) {
            return cachedUser;
        }
        
        // Fallback to database
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                cacheUser(user); // Cache for future use
                return user;
            }
        }
        
        logger.warn("Failed authentication attempt for user: {}", username);
        throw new RuntimeException("Invalid credentials");
    }
    
    public User updateUser(Long userId, User updatedUser) {
        logger.info("Updating user: {}", userId);
        
        User existingUser = getUserById(userId);
        if (existingUser == null) {
            throw new RuntimeException("User not found");
        }
        
        // Update fields efficiently
        updateUserFields(existingUser, updatedUser);
        existingUser.setUpdatedAt(LocalDateTime.now());
        
        User savedUser = userRepository.save(existingUser);
        
        // Update cache
        cacheUser(savedUser);
        
        logger.info("Successfully updated user: {}", userId);
        return savedUser;
    }
    
    private void updateUserFields(User existing, User updated) {
        if (updated.getEmail() != null) existing.setEmail(updated.getEmail());
        if (updated.getPhoneNumber() != null) existing.setPhoneNumber(updated.getPhoneNumber());
        if (updated.getCity() != null) existing.setCity(updated.getCity());
        if (updated.getAlertThreshold() != null) existing.setAlertThreshold(updated.getAlertThreshold());
    }
    
    public User getUserById(Long userId) {
        // O(1) cache lookup
        User cachedUser = userCache.get(userId);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // Database fallback
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            cacheUser(user);
            return user;
        }
        
        return null;
    }
    
    public User getUserByUsername(String username) {
        // O(1) cache lookup
        User cachedUser = usernameCache.get(username);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // Database fallback
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            cacheUser(user);
            return user;
        }
        
        return null;
    }
    
    public boolean deleteUser(Long userId) {
        try {
            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
                
                // Remove from cache
                User removedUser = userCache.remove(userId);
                if (removedUser != null) {
                    usernameCache.remove(removedUser.getUsername());
                }
                
                logger.info("Successfully deleted user: {}", userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private void cacheUser(User user) {
        if (user != null) {
            userCache.put(user.getId(), user);
            usernameCache.put(user.getUsername(), user);
        }
    }
    
    public User updateUserCredentials(Long userId, CredentialChangeRequest request) {
        logger.info("Updating credentials for user: {}", userId);
        
        // Validate current password first
        User existingUser = getUserById(userId);
        if (existingUser == null) {
            throw new RuntimeException("User not found");
        }
        
        // Verify current password
        if (request.getCurrentPassword() == null || 
            !passwordEncoder.matches(request.getCurrentPassword(), existingUser.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }
        
        // Update credentials based on what's provided
        boolean updated = false;
        
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            // Check for existing email
            if (userRepository.existsByEmail(request.getEmail()) && 
                !request.getEmail().equals(existingUser.getEmail())) {
                throw new RuntimeException("Email already exists");
            }
            existingUser.setEmail(request.getEmail());
            updated = true;
        }
        
        if (request.getNewPassword() != null && !request.getNewPassword().trim().isEmpty()) {
            // Validate new password strength
            if (request.getNewPassword().length() < 6) {
                throw new RuntimeException("New password must be at least 6 characters long");
            }
            existingUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
            updated = true;
        }
        
        if (request.getPhoneNumber() != null) {
            existingUser.setPhoneNumber(request.getPhoneNumber());
            updated = true;
        }
        
        if (request.getCity() != null && !request.getCity().trim().isEmpty()) {
            existingUser.setCity(request.getCity());
            updated = true;
        }
        
        if (request.getAlertThreshold() != null) {
            if (request.getAlertThreshold() < 0 || request.getAlertThreshold() > 500) {
                throw new RuntimeException("Alert threshold must be between 0 and 500");
            }
            existingUser.setAlertThreshold(request.getAlertThreshold());
            updated = true;
        }
        
        if (!updated) {
            throw new RuntimeException("No valid credentials provided for update");
        }
        
        existingUser.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(existingUser);
        
        // Update cache
        cacheUser(savedUser);
        
        logger.info("Successfully updated credentials for user: {}", userId);
        return savedUser;
    }

    public void clearCache() {
        userCache.clear();
        usernameCache.clear();
        logger.info("User cache cleared");
    }
}