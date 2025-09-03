package com.air.airquality.services;

import com.air.airquality.model.AqiData;
import com.air.airquality.model.User;
import com.air.airquality.model.UserAlert;
import com.air.airquality.repository.UserAlertRepository;
import com.air.airquality.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    
    // Cache for user alerts to improve performance
    private final ConcurrentHashMap<Long, List<UserAlert>> userAlertsCache = new ConcurrentHashMap<>();
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserAlertRepository userAlertRepository;
    
    @Autowired
    private OpenAQService openAQService;
    
    @Value("${twilio.account.sid:}")
    private String accountSid;
    
    @Value("${twilio.auth.token:}")
    private String authToken;
    
    @Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;
    
    private volatile boolean twilioEnabled = false;
    
    @PostConstruct
    public void initTwilio() {
        twilioEnabled = accountSid != null && !accountSid.isEmpty() && 
                       authToken != null && !authToken.isEmpty() &&
                       twilioPhoneNumber != null && !twilioPhoneNumber.isEmpty();
        
        if (twilioEnabled) {
            logger.info("Twilio SMS service configured successfully");
        } else {
            logger.info("Twilio credentials not provided, SMS alerts disabled");
        }
    }
    
    // Process all alerts asynchronously
    public void processAlerts() {
        try {
            List<User> allUsers = userRepository.findAll();
            
            // Group users by city for batch processing
            allUsers.parallelStream()
                    .collect(Collectors.groupingBy(User::getCity))
                    .forEach((city, users) -> 
                        CompletableFuture.runAsync(() -> checkCityAlerts(city, users)));
                        
        } catch (Exception e) {
            logger.error("Error processing alerts: {}", e.getMessage());
        }
    }
    
    private void checkCityAlerts(String city, List<User> users) {
        try {
            AqiData aqiData = openAQService.getCurrentAqiData(city);
            if (aqiData == null || aqiData.getAqiValue() == null) return;
            
            Integer currentAqi = aqiData.getAqiValue();
            
            // Filter users who need alerts using streams
            List<User> usersToAlert = users.stream()
                    .filter(user -> currentAqi >= user.getAlertThreshold())
                    .collect(Collectors.toList());
            
            if (!usersToAlert.isEmpty()) {
                logger.info("Alerting {} users for city: {} with AQI: {}", 
                          usersToAlert.size(), city, currentAqi);
                
                // Send alerts in parallel
                usersToAlert.parallelStream()
                          .forEach(user -> sendAlert(user, city, currentAqi));
            }
            
        } catch (Exception e) {
            logger.error("Error checking alerts for city {}: {}", city, e.getMessage());
        }
    }
    
    public void checkAndSendAlerts(String city, Integer aqiValue) {
        try {
            List<User> usersToAlert = userRepository.findUsersForAlert(city, aqiValue);
            
            if (usersToAlert.isEmpty()) {
                logger.debug("No users to alert for city: {} with AQI: {}", city, aqiValue);
                return;
            }
            
            logger.info("Found {} users to alert for city: {} with AQI: {}", 
                       usersToAlert.size(), city, aqiValue);
            
            // Process alerts in parallel for better performance
            usersToAlert.parallelStream()
                       .forEach(user -> sendAlert(user, city, aqiValue));
            
        } catch (Exception e) {
            logger.error("Error checking and sending alerts for city {}: {}", city, e.getMessage());
        }
    }
    
    private void sendAlert(User user, String city, Integer aqiValue) {
        try {
            boolean alertSent = false;
            
            if (twilioEnabled) {
                sendSmsAlert(user, city, aqiValue);
                alertSent = true;
                logger.info("SMS alert sent to user: {} for city: {}", user.getUsername(), city);
            } else {
                logAlert(user, city, aqiValue);
                alertSent = true;
                logger.info("Alert logged for user: {} for city: {} (SMS disabled)", 
                          user.getUsername(), city);
            }
            
            // Save alert record and invalidate cache
            saveAlertRecord(user, city, aqiValue, alertSent);
            userAlertsCache.remove(user.getId());
            
        } catch (Exception e) {
            logger.error("Failed to send alert to user {}: {}", user.getUsername(), e.getMessage());
            saveAlertRecord(user, city, aqiValue, false);
        }
    }
    
    private void saveAlertRecord(User user, String city, Integer aqiValue, boolean alertSent) {
        try {
            UserAlert alert = new UserAlert(user, city, aqiValue, user.getAlertThreshold());
            alert.setAlertSent(alertSent);
            userAlertRepository.save(alert);
        } catch (Exception e) {
            logger.error("Failed to save alert record: {}", e.getMessage());
        }
    }
    
    private void sendSmsAlert(User user, String city, Integer aqiValue) {
        String messageBody = String.format(
            "🚨 AIR QUALITY ALERT!\nCity: %s\nCurrent AQI: %d\nYour threshold: %d\n" +
            "Category: %s\nPlease take necessary precautions!\n- AirSight Monitoring",
            city, aqiValue, user.getAlertThreshold(), openAQService.getAqiCategory(aqiValue)
        );
        
        try {
            // Placeholder for actual Twilio SMS sending
            logger.info("SMS Alert for {}: {}", user.getPhoneNumber(), 
                       messageBody.replace("\n", " | "));
            
        } catch (Exception e) {
            logger.error("Failed to send SMS to {}: {}", user.getPhoneNumber(), e.getMessage());
            throw e;
        }
    }
    
    private void logAlert(User user, String city, Integer aqiValue) {
        String alertMessage = String.format(
            "ALERT: User %s (%s) - City: %s, AQI: %d (threshold: %d), Category: %s",
            user.getUsername(), user.getPhoneNumber(), city, aqiValue,
            user.getAlertThreshold(), openAQService.getAqiCategory(aqiValue)
        );
        
        logger.warn("AIR QUALITY ALERT: {}", alertMessage);
    }
    
    public List<UserAlert> getUserAlerts(Long userId) {
        // Use cache for O(1) lookup
        return userAlertsCache.computeIfAbsent(userId, id -> {
            try {
                return userAlertRepository.findByUserIdOrderByCreatedAtDesc(id);
            } catch (Exception e) {
                logger.error("Error getting alerts for user {}: {}", id, e.getMessage());
                return List.of();
            }
        });
    }
    
    public void deleteUserAlert(Long alertId, Long userId) {
        try {
            UserAlert alert = userAlertRepository.findByIdAndUserId(alertId, userId);
            if (alert != null) {
                userAlertRepository.delete(alert);
                userAlertsCache.remove(userId); // Invalidate cache
                logger.info("Deleted alert {} for user {}", alertId, userId);
            } else {
                logger.warn("Alert {} not found for user {}", alertId, userId);
            }
        } catch (Exception e) {
            logger.error("Error deleting alert {} for user {}: {}", alertId, userId, e.getMessage());
        }
    }
}