package com.air.airquality.services;

import com.air.airquality.model.AqiData;
import com.air.airquality.repository.AqiDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Order(1) // Execute early in the startup process
public class HistoricalDataSeederService implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(HistoricalDataSeederService.class);
    
    @Autowired
    private AqiDataRepository aqiDataRepository;
    
    // Flag to prevent concurrent seeding operations
    private volatile boolean isSeeding = false;
    
    // Configuration to disable seeding in test environment
    @Value("${app.historical-data.seed.enabled:true}")
    private boolean seedingEnabled;
    
    // Cities with their typical AQI ranges and seasonal patterns
    private final Map<String, CityProfile> cityProfiles = Map.of(
        "Delhi", new CityProfile(120, 80, 0.7, 0.4), // High pollution, high variation, seasonal
        "Mumbai", new CityProfile(90, 60, 0.5, 0.3),
        "Chennai", new CityProfile(70, 45, 0.4, 0.2),
        "London", new CityProfile(50, 30, 0.3, 0.15),
        "New York", new CityProfile(60, 35, 0.4, 0.2),
        "Beijing", new CityProfile(110, 75, 0.6, 0.35),
        "Los Angeles", new CityProfile(80, 50, 0.4, 0.25),
        "Tokyo", new CityProfile(65, 40, 0.3, 0.18),
        "Paris", new CityProfile(55, 35, 0.3, 0.2),
        "Sydney", new CityProfile(45, 28, 0.25, 0.15)
    );
    
    @Override
    public void run(String... args) throws Exception {
        // Skip seeding if disabled (e.g., in test environment)
        if (!seedingEnabled) {
            logger.info("Historical data seeding is disabled");
            return;
        }
        
        try {
            // Check if we already have historical data
            long existingRecords = aqiDataRepository.count();
            LocalDateTime threeYearsAgo = LocalDateTime.now().minus(3, ChronoUnit.YEARS);
            
            // Only seed if we have very limited data (changed threshold)
            if (existingRecords < 1000) { // Reduced threshold to prevent excessive seeding
                logger.info("Database has {} records, starting historical data seeding for past 3 years...", existingRecords);
                generateHistoricalData(threeYearsAgo, LocalDateTime.now());
                logger.info("Historical data seeding completed successfully");
            } else {
                logger.info("Database already has {} records, skipping historical data seeding", existingRecords);
            }
        } catch (Exception e) {
            logger.error("Error during historical data seeding: {}", e.getMessage(), e);
            // Don't fail the application startup
        }
    }
    
    public void generateHistoricalData(LocalDateTime startDate, LocalDateTime endDate) {
        // Prevent concurrent seeding operations
        if (isSeeding) {
            logger.warn("Historical data generation already in progress, skipping request");
            return;
        }
        
        isSeeding = true;
        
        try {
            // Check if we already have data in this range to avoid duplicates
            long existingDataInRange = aqiDataRepository.count();
            
            List<AqiData> batchData = new ArrayList<>();
            int batchSize = 500; // Reduced batch size for better performance
            int totalRecords = 0;
            
            logger.info("Starting data generation with batch size: {}", batchSize);
            
            for (String city : cityProfiles.keySet()) {
                logger.info("Generating historical data for {} from {} to {}", city, startDate, endDate);
                
                LocalDateTime currentTime = startDate;
                CityProfile profile = cityProfiles.get(city);
                int cityRecords = 0;
                
                while (currentTime.isBefore(endDate) && cityRecords < 1000) { // Limit records per city
                    AqiData data = generateDataPoint(city, currentTime, profile);
                    batchData.add(data);
                    cityRecords++;
                    
                    // Save in batches for performance
                    if (batchData.size() >= batchSize) {
                        try {
                            aqiDataRepository.saveAll(batchData);
                            totalRecords += batchData.size();
                            batchData.clear();
                            
                            if (totalRecords % 1000 == 0) {
                                logger.info("Generated {} historical records so far...", totalRecords);
                            }
                        } catch (Exception e) {
                            logger.warn("Error saving batch of historical data: {}", e.getMessage());
                            batchData.clear(); // Clear the problematic batch and continue
                        }
                    }
                    
                    // Generate data every 12-24 hours to match our update schedule
                    currentTime = currentTime.plusHours(12 + (int)(Math.random() * 12));
                }
                
                logger.info("Generated {} records for city: {}", cityRecords, city);
            }
            
            // Save remaining data
            if (!batchData.isEmpty()) {
                try {
                    aqiDataRepository.saveAll(batchData);
                    totalRecords += batchData.size();
                } catch (Exception e) {
                    logger.warn("Error saving final batch of historical data: {}", e.getMessage());
                }
            }
            
            logger.info("Generated {} total historical records for date range {} to {}", totalRecords, startDate, endDate);
        } finally {
            isSeeding = false;
        }
    }
    
    private AqiData generateDataPoint(String city, LocalDateTime timestamp, CityProfile profile) {
        Random random = ThreadLocalRandom.current();
        
        // Base AQI with seasonal and time-of-day variations
        double seasonalMultiplier = getSeasonalMultiplier(timestamp, profile.seasonalVariation);
        double timeOfDayMultiplier = getTimeOfDayMultiplier(timestamp.getHour());
        
        // Generate base AQI with variations
        double baseAqi = profile.averageAqi * seasonalMultiplier * timeOfDayMultiplier;
        int aqi = Math.max(10, Math.min(500, 
            (int) (baseAqi + random.nextGaussian() * profile.stdDeviation)));
        
        // Generate pollutant values based on AQI
        AqiData data = new AqiData();
        data.setCity(city);
        data.setTimestamp(timestamp);
        data.setAqiValue(aqi);
        
        // Generate realistic pollutant values
        data.setPm25(generatePollutant(aqi, 0.4, 5.0, 150.0));
        data.setPm10(generatePollutant(aqi, 0.6, 10.0, 250.0));
        data.setNo2(generatePollutant(aqi, 0.3, 5.0, 100.0));
        data.setSo2(generatePollutant(aqi, 0.2, 2.0, 80.0));
        data.setCo(generatePollutant(aqi, 0.1, 0.5, 20.0));
        data.setO3(generatePollutant(aqi, 0.35, 10.0, 200.0));
        
        return data;
    }
    
    private double getSeasonalMultiplier(LocalDateTime timestamp, double seasonalVariation) {
        Month month = timestamp.getMonth();
        
        // Winter months typically have higher pollution
        double baseSeasonal = 1.0;
        switch (month) {
            case DECEMBER, JANUARY, FEBRUARY:
                baseSeasonal = 1.0 + seasonalVariation; // Higher pollution in winter
                break;
            case MARCH, APRIL, MAY:
                baseSeasonal = 1.0 + seasonalVariation * 0.3; // Moderate in spring
                break;
            case JUNE, JULY, AUGUST:
                baseSeasonal = 1.0 - seasonalVariation * 0.2; // Lower in summer (more rain)
                break;
            case SEPTEMBER, OCTOBER, NOVEMBER:
                baseSeasonal = 1.0 + seasonalVariation * 0.5; // Moderate to high in autumn
                break;
        }
        
        return Math.max(0.3, Math.min(2.0, baseSeasonal));
    }
    
    private double getTimeOfDayMultiplier(int hour) {
        // Morning and evening rush hours have higher pollution
        if ((hour >= 7 && hour <= 10) || (hour >= 17 && hour <= 20)) {
            return 1.3; // Rush hour increase
        } else if (hour >= 2 && hour <= 5) {
            return 0.7; // Lower pollution in early morning
        } else {
            return 1.0; // Normal levels
        }
    }
    
    private Double generatePollutant(int aqi, double factor, double minValue, double maxValue) {
        Random random = ThreadLocalRandom.current();
        
        // Base value proportional to AQI
        double baseValue = (aqi * factor) + minValue;
        
        // Add some random variation
        double variation = baseValue * 0.3 * random.nextGaussian();
        double finalValue = Math.max(0, Math.min(maxValue, baseValue + variation));
        
        // Sometimes return null to simulate missing sensor data
        return random.nextDouble() < 0.95 ? Math.round(finalValue * 100.0) / 100.0 : null;
    }
    
    // Helper class to define city characteristics
    private static class CityProfile {
        final double averageAqi;
        final double stdDeviation;
        final double seasonalVariation;
        final double dailyVariation;
        
        CityProfile(double averageAqi, double stdDeviation, double seasonalVariation, double dailyVariation) {
            this.averageAqi = averageAqi;
            this.stdDeviation = stdDeviation;
            this.seasonalVariation = seasonalVariation;
            this.dailyVariation = dailyVariation;
        }
    }
}
