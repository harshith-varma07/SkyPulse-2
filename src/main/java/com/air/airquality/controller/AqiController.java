package com.air.airquality.controller;

import com.air.airquality.dto.AqiResponse;
import com.air.airquality.model.AqiData;
import com.air.airquality.services.AqiService;
import com.air.airquality.services.OpenAQService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/aqi")
public class AqiController {
    
    private static final Logger logger = LoggerFactory.getLogger(AqiController.class);
    private static final int CACHE_DURATION_MINUTES = 5;
    
    @Autowired
    private AqiService aqiService;
    @Autowired
    private OpenAQService openAQService;
    
    // Optimized caching with ConcurrentHashMap for thread safety
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    
    @GetMapping("/current/{city}")
    public ResponseEntity<Map<String, Object>> getCurrentAqi(@PathVariable String city) {
        String normalizedCity = normalizeCity(city);
        
        // Check cache first - O(1) lookup
        CachedResponse cachedData = cache.get(normalizedCity);
        if (cachedData != null && !cachedData.isExpired()) {
            logger.debug("Cache hit for city: {}", normalizedCity);
            return ResponseEntity.ok(cachedData.getData());
        }
        
        try {
            AqiData aqiData = openAQService.getCurrentAqiData(normalizedCity);
            Map<String, Object> response = buildSuccessResponse(aqiData);
            
            // Cache the response
            cache.put(normalizedCity, new CachedResponse(response));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching AQI for city {}: {}", normalizedCity, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse("Unable to fetch AQI data for " + city, e.getMessage()));
        }
    }

    @GetMapping("/cities")
    public ResponseEntity<Map<String, Object>> getAvailableCities() {
        try {
            List<String> cities = openAQService.getAvailableCities();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cities", cities);
            response.put("count", cities.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching cities: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse("Unable to fetch available cities", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchCities(@RequestParam String query) {
        try {
            String normalizedQuery = normalizeCity(query.trim());
            List<String> matchingCities = openAQService.searchCities(normalizedQuery);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cities", matchingCities);
            response.put("query", query);
            response.put("found", matchingCities.size());
            
            // If cities found, get current data for the first one
            if (!matchingCities.isEmpty()) {
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return openAQService.getCurrentAqiData(matchingCities.get(0));
                    } catch (Exception e) {
                        return null;
                    }
                }).thenAccept(aqiData -> {
                    if (aqiData != null) {
                        response.put("currentData", buildAqiResponse(aqiData));
                    }
                });
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Search error for query '{}': {}", query, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Search failed", e.getMessage()));
        }
    }

    @GetMapping("/multiple")
    public ResponseEntity<Map<String, Object>> getMultipleCitiesAqi(@RequestParam List<String> cities) {
        try {
            // Parallel processing for better performance
            Map<String, AqiResponse> citiesData = cities.parallelStream()
                .collect(Collectors.toConcurrentMap(
                    city -> city,
                    city -> {
                        try {
                            AqiData aqiData = openAQService.getCurrentAqiData(normalizeCity(city));
                            return buildAqiResponse(aqiData);
                        } catch (Exception e) {
                            logger.warn("Failed to get data for city: {}", city);
                            return null;
                        }
                    }
                ));
            
            // Remove null values
            citiesData.values().removeIf(Objects::isNull);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", citiesData);
            response.put("count", citiesData.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching multiple cities AQI: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Unable to fetch data for requested cities", e.getMessage()));
        }
    }

    @PostMapping("/cities/add")
    public ResponseEntity<Map<String, Object>> addCityToMonitoring(@RequestParam String city) {
        try {
            String normalizedCity = normalizeCity(city);
            boolean success = openAQService.addCityToMonitoring(normalizedCity);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                AqiData aqiData = openAQService.getCurrentAqiData(normalizedCity);
                response.put("success", true);
                response.put("message", "City added successfully");
                response.put("data", buildAqiResponse(aqiData));
                
                // Clear cache to refresh city list
                cache.clear();
            } else {
                response.put("success", false);
                response.put("message", "Failed to add city");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding city {}: {}", city, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Error adding city", e.getMessage()));
        }
    }

    @GetMapping("/historical/{city}")
    public ResponseEntity<Map<String, Object>> getHistoricalData(
            @PathVariable String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Authentication required");
            response.put("requiresAuth", true);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        try {
            String normalizedCity = normalizeCity(city);
            
            // Get data availability info
            var availabilityInfo = aqiService.getDataAvailabilityInfo(normalizedCity);
            
            if (!availabilityInfo.isHasData()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "No historical data available for " + normalizedCity);
                response.put("dataAvailable", false);
                response.put("suggestDataGeneration", true);
                return ResponseEntity.ok(response);
            }
            
            // Default to available data range if dates not provided
            if (endDate == null) {
                endDate = availabilityInfo.getNewestDate() != null ? 
                    availabilityInfo.getNewestDate() : LocalDateTime.now();
            }
            if (startDate == null) {
                startDate = availabilityInfo.getOldestDate() != null ? 
                    availabilityInfo.getOldestDate() : endDate.minusDays(90);
            }
            
            // Validate that requested dates are within available data range
            if (availabilityInfo.getOldestDate() != null && startDate.isBefore(availabilityInfo.getOldestDate())) {
                startDate = availabilityInfo.getOldestDate();
                logger.info("Adjusted start date to oldest available data: {}", startDate);
            }
            if (availabilityInfo.getNewestDate() != null && endDate.isAfter(availabilityInfo.getNewestDate())) {
                endDate = availabilityInfo.getNewestDate();
                logger.info("Adjusted end date to newest available data: {}", endDate);
            }
            
            // Validate date range to prevent excessive data requests (max 18 months)
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 548) { // 18 months ≈ 548 days
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Date range cannot exceed 18 months (548 days). Data retention period is 18 months.");
                response.put("maxDaysAllowed", 548);
                response.put("requestedDays", daysBetween);
                return ResponseEntity.badRequest().body(response);
            }
            
            // Log warning for large ranges
            if (daysBetween > 365) {
                logger.warn("Large date range requested for city {}: {} days", normalizedCity, daysBetween);
            }
            
            List<AqiResponse> historicalData = aqiService.getHistoricalData(normalizedCity, startDate, endDate);
            
            // Implement data sampling for very large datasets to improve frontend performance
            boolean wasSampled = false;
            if (historicalData.size() > 10000) {
                logger.info("Sampling large dataset for city {}: {} records", normalizedCity, historicalData.size());
                // Sample every nth record to keep dataset manageable
                int step = Math.max(1, historicalData.size() / 10000);
                List<AqiResponse> sampledData = new ArrayList<>();
                for (int i = 0; i < historicalData.size(); i += step) {
                    sampledData.add(historicalData.get(i));
                }
                historicalData = sampledData;
                wasSampled = true;
                logger.info("Sampled to {} records for better performance", historicalData.size());
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", historicalData);
            response.put("count", historicalData.size());
            response.put("city", normalizedCity);
            response.put("startDate", startDate);
            response.put("endDate", endDate);
            response.put("daysCovered", daysBetween);
            response.put("wasSampled", wasSampled);
            
            // Add data availability info to response
            response.put("dataAvailability", Map.of(
                "oldestDate", availabilityInfo.getOldestDate(),
                "newestDate", availabilityInfo.getNewestDate(),
                "totalRecords", availabilityInfo.getRecordCount(),
                "retentionPeriod", "18 months"
            ));
            
            if (wasSampled) {
                response.put("note", "Large dataset was sampled for optimal performance");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching historical data for {}: {}", city, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Unable to fetch historical data", e.getMessage()));
        }
    }

    @GetMapping("/data-availability")
    public ResponseEntity<Map<String, Object>> getDataAvailability(@RequestParam(required = false) String city) {
        try {
            var availabilityInfo = aqiService.getDataAvailabilityInfo(city);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("city", city);
            response.put("hasData", availabilityInfo.isHasData());
            response.put("oldestDate", availabilityInfo.getOldestDate());
            response.put("newestDate", availabilityInfo.getNewestDate());
            response.put("recordCount", availabilityInfo.getRecordCount());
            response.put("dataPeriodDays", availabilityInfo.getDataPeriodInDays());
            response.put("retentionPeriod", "18 months");
            response.put("maxDateRange", "18 months (548 days)");
            
            if (!availabilityInfo.isHasData()) {
                response.put("message", "No data available. Consider generating historical data.");
                response.put("suggestDataGeneration", true);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching data availability info: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("Unable to fetch data availability information", e.getMessage()));
        }
    }

    // Utility methods
    private String normalizeCity(String city) {
        return Arrays.stream(city.toLowerCase().trim().split("\\s+"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private Map<String, Object> buildSuccessResponse(AqiData aqiData) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", buildAqiResponse(aqiData));
        response.put("message", "Data retrieved successfully");
        return response;
    }

    private AqiResponse buildAqiResponse(AqiData aqiData) {
        AqiResponse response = new AqiResponse(
            aqiData.getCity(),
            aqiData.getAqiValue(),
            aqiData.getPm25(),
            aqiData.getPm10(),
            aqiData.getNo2(),
            aqiData.getSo2(),
            aqiData.getCo(),
            aqiData.getO3(),
            aqiData.getTimestamp()
        );
        response.setCategory(openAQService.getAqiCategory(aqiData.getAqiValue()));
        response.setDescription(openAQService.getAqiDescription(aqiData.getAqiValue()));
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message, String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", error);
        return response;
    }

    // Inner class for caching
    private static class CachedResponse {
        private final Map<String, Object> data;
        private final LocalDateTime timestamp;

        public CachedResponse(Map<String, Object> data) {
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }

        public boolean isExpired() {
            return timestamp.isBefore(LocalDateTime.now().minusMinutes(CACHE_DURATION_MINUTES));
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}
