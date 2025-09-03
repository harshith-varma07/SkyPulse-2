package com.air.airquality.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Service that runs after Spring Boot application startup to ensure
 * we have sufficient historical data for analytics
 */
@Service
public class StartupDataService implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupDataService.class);
    
    @Autowired
    private AqiService aqiService;
    
    @Autowired
    private HistoricalDataSeederService historicalDataSeederService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting post-deployment data initialization check...");
        
        try {
            // Check if we have sufficient data
            long totalRecords = aqiService.getTotalRecords();
            logger.info("Current database contains {} AQI records", totalRecords);
            
            // If we have very little data, generate some historical data
            if (totalRecords < 100) {
                logger.info("Insufficient historical data detected. Generating 18 months of sample data...");
                
                LocalDateTime eighteenMonthsAgo = LocalDateTime.now().minusMonths(18);
                LocalDateTime now = LocalDateTime.now();
                
                // Generate historical data in a separate thread to avoid blocking startup
                new Thread(() -> {
                    try {
                        historicalDataSeederService.generateHistoricalData(eighteenMonthsAgo, now);
                        long newTotalRecords = aqiService.getTotalRecords();
                        logger.info("Historical data generation completed. Total records: {}", newTotalRecords);
                    } catch (Exception e) {
                        logger.error("Error generating historical data during startup: {}", e.getMessage(), e);
                    }
                }).start();
                
                logger.info("Historical data generation started in background thread");
            } else {
                logger.info("Sufficient historical data exists. Skipping data generation.");
            }
            
        } catch (Exception e) {
            logger.error("Error during startup data initialization: {}", e.getMessage(), e);
        }
        
        logger.info("Post-deployment data initialization check completed");
    }
}
