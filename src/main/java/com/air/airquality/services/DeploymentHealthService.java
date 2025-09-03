package com.air.airquality.services;

import com.air.airquality.repository.AqiDataRepository;
import com.air.airquality.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class DeploymentHealthService {
    
    private static final Logger logger = LoggerFactory.getLogger(DeploymentHealthService.class);
    
    @Autowired
    private AqiDataRepository aqiDataRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OpenAQService openAQService;
    
    /**
     * Get comprehensive system status for monitoring
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Database metrics
            long totalRecords = aqiDataRepository.count();
            long usersCount = userRepository.count();
            int citiesCount = openAQService.getAvailableCities().size();
            
            // Check data freshness
            LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
            long recentRecords = aqiDataRepository.countByTimestampAfter(yesterday);
            
            status.put("database", Map.of(
                "totalAqiRecords", totalRecords,
                "totalUsers", usersCount,
                "citiesMonitored", citiesCount,
                "recentRecords", recentRecords,
                "dataFresh", recentRecords > 0,
                "status", "healthy"
            ));
            
            // System metrics
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            status.put("system", Map.of(
                "memoryUsed", formatBytes(usedMemory),
                "memoryTotal", formatBytes(totalMemory),
                "memoryFree", formatBytes(freeMemory),
                "memoryUsagePercent", Math.round((double) usedMemory / totalMemory * 100),
                "processors", runtime.availableProcessors()
            ));
            
            // API status
            boolean apiHealthy = openAQService.isApiHealthy();
            status.put("externalServices", Map.of(
                "openAQ", apiHealthy ? "UP" : "DOWN",
                "fallbackAvailable", true,
                "lastChecked", LocalDateTime.now()
            ));
            
            status.put("overall", "healthy");
            status.put("timestamp", LocalDateTime.now());
            status.put("version", "1.0.0");
            
        } catch (Exception e) {
            logger.error("Error getting system status: {}", e.getMessage());
            status.put("overall", "error");
            status.put("error", e.getMessage());
            status.put("timestamp", LocalDateTime.now());
        }
        
        return status;
    }
    
    /**
     * Simple health check for deployment
     */
    public boolean isHealthy() {
        try {
            long recordCount = aqiDataRepository.count();
            int cityCount = openAQService.getAvailableCities().size();
            return recordCount > 0 && cityCount > 0;
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), units[exp]);
    }
}