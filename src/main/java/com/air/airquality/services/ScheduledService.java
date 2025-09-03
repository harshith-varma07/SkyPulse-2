package com.air.airquality.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.List;

@Service
public class ScheduledService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledService.class);
    
    @Autowired
    private OpenAQService openAQService;
    
    @Autowired
    private AlertService alertService;
    
    @Autowired
    private AqiService aqiService;
    
    @Autowired
    private HistoricalDataSeederService historicalDataSeederService;
    
    // Flag to prevent overlapping executions
    private volatile boolean isUpdating = false;
    
    // Fixed scheduled task - properly waits for completion
    @Scheduled(fixedRate = 43200000, initialDelay = 60000) // Every 12 hours (43200000ms), 1 minute initial delay
    public void updateAqiData() {
        // Prevent overlapping executions
        if (isUpdating) {
            logger.warn("Previous update still in progress, skipping this execution");
            return;
        }
        
        isUpdating = true;
        logger.info("Starting scheduled AQI data update");
        
        try {
            // Run data update and alert processing asynchronously
            CompletableFuture<Void> updateTask = CompletableFuture.runAsync(() -> {
                try {
                    openAQService.updateAllCitiesData();
                } catch (Exception e) {
                    logger.error("Error in update task: {}", e.getMessage(), e);
                }
            });
            
            CompletableFuture<Void> alertTask = CompletableFuture.runAsync(() -> {
                try {
                    alertService.processAlerts();
                } catch (Exception e) {
                    logger.error("Error in alert task: {}", e.getMessage(), e);
                }
            });
            
            // IMPORTANT: Actually wait for both tasks to complete before the method returns
            CompletableFuture.allOf(updateTask, alertTask).join();
            logger.info("Scheduled update completed successfully");
                    
        } catch (Exception e) {
            logger.error("Error in scheduled service: {}", e.getMessage(), e);
        } finally {
            isUpdating = false;
        }
    }

    // Cleanup old data daily - maintain 18 months of data
    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    public void cleanupOldData() {
        logger.info("Starting daily data cleanup - maintaining 18 months of data");
        try {
            aqiService.cleanupOldData(548); // Keep 18 months of data (18 * 30.44 ≈ 548 days)
            logger.info("Daily cleanup completed - data older than 18 months removed");
        } catch (Exception e) {
            logger.error("Error during cleanup: {}", e.getMessage());
        }
    }
    
    // Ensure we maintain 18 months of historical data - weekly check (disabled temporarily)
    // @Scheduled(cron = "0 0 4 * * SUN") // Every Sunday at 4 AM  
    public void ensureHistoricalDataIntegrity() {
        logger.info("Starting weekly historical data integrity check");
        try {
            long totalRecords = aqiService.getTotalRecords();
            
            // Check if we need to generate more historical data (more conservative threshold)
            // For 10 cities, 18 months, with data every 12-24 hours = ~5,400-10,800 records expected
            if (totalRecords < 3000) {
                logger.info("Historical data appears insufficient ({} records), considering data generation", totalRecords);
                
                // Only generate if we have very little data to prevent continuous seeding
                if (totalRecords < 500) {
                    LocalDateTime eighteenMonthsAgo = LocalDateTime.now().minusMonths(18);
                    LocalDateTime now = LocalDateTime.now();
                    
                    // Generate any missing historical data
                    historicalDataSeederService.generateHistoricalData(eighteenMonthsAgo, now);
                    
                    long newTotalRecords = aqiService.getTotalRecords();
                    logger.info("Historical data integrity check completed - now have {} records", newTotalRecords);
                } else {
                    logger.info("Historical data integrity check skipped - sufficient data exists ({} records)", totalRecords);
                }
            } else {
                logger.info("Historical data integrity check passed - {} records available", totalRecords);
            }
            
        } catch (Exception e) {
            logger.error("Error during historical data integrity check: {}", e.getMessage(), e);
        }
    }
}