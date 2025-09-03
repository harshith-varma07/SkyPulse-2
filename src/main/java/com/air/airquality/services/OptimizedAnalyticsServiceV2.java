package com.air.airquality.services;

import com.air.airquality.dto.AqiResponse;
import com.air.airquality.model.AqiData;
import com.air.airquality.repository.AqiDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OptimizedAnalyticsServiceV2 {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedAnalyticsServiceV2.class);
    
    @Autowired
    private AqiDataRepository aqiDataRepository;
    
    @Autowired
    private AqiService aqiService;
    
    // Efficient data structures for caching
    private final Map<String, CachedStats> statsCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 300000; // 5 minutes
    
    /**
     * Get comprehensive analytics statistics for a city and date range
     * Optimized for database queries and efficient data processing
     */
    public Map<String, Object> getAnalyticsStats(String city, LocalDateTime startDate, LocalDateTime endDate) {
        String cacheKey = buildCacheKey(city, startDate, endDate);
        
        // Check cache first
        CachedStats cached = statsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Cache hit for analytics stats: {}", cacheKey);
            return cached.getStats();
        }
        
        try {
            // Get historical data efficiently
            List<AqiResponse> data = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (data.isEmpty()) {
                return createErrorResponse("No data available for " + city + " in the specified period");
            }
            
            // Process data efficiently using streams and collectors
            Map<String, Object> stats = processDataForStatistics(data, city, startDate, endDate);
            
            // Cache the results
            statsCache.put(cacheKey, new CachedStats(stats));
            
            // Clean up old cache entries
            cleanupExpiredCache();
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Error generating analytics stats for city {}: {}", city, e.getMessage(), e);
            return createErrorResponse("Failed to generate analytics: " + e.getMessage());
        }
    }
    
    /**
     * Generate trend analysis chart data (returns JSON data instead of images for better performance)
     */
    public Map<String, Object> getTrendChartData(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<AqiResponse> data = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (data.isEmpty()) {
                return createErrorResponse("No data available for trend analysis");
            }
            
            // Sort by timestamp for proper trend analysis
            data.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
            
            // Efficiently sample data for chart if too many points
            List<AqiResponse> sampledData = sampleDataForChart(data, 100); // Max 100 points for performance
            
            return Map.of(
                "success", true,
                "chartType", "trend",
                "city", city,
                "dataPoints", sampledData.stream().map(d -> Map.of(
                    "timestamp", d.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "aqi", d.getAqiValue(),
                    "pm25", d.getPm25() != null ? d.getPm25() : 0,
                    "pm10", d.getPm10() != null ? d.getPm10() : 0,
                    "category", d.getAqiCategory()
                )).collect(Collectors.toList()),
                "period", Map.of("start", startDate, "end", endDate),
                "samplingApplied", data.size() > 100
            );
            
        } catch (Exception e) {
            logger.error("Error generating trend chart data for city {}: {}", city, e.getMessage());
            return createErrorResponse("Failed to generate trend chart: " + e.getMessage());
        }
    }
    
    /**
     * Get pollutant distribution analysis
     */
    public Map<String, Object> getPollutantDistribution(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<AqiResponse> data = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (data.isEmpty()) {
                return createErrorResponse("No data available for pollutant analysis");
            }
            
            // Calculate pollutant statistics efficiently
            Map<String, Map<String, Double>> pollutantStats = calculatePollutantStatistics(data);
            
            return Map.of(
                "success", true,
                "city", city,
                "pollutants", pollutantStats,
                "period", Map.of("start", startDate, "end", endDate),
                "totalRecords", data.size()
            );
            
        } catch (Exception e) {
            logger.error("Error calculating pollutant distribution for city {}: {}", city, e.getMessage());
            return createErrorResponse("Failed to analyze pollutants: " + e.getMessage());
        }
    }
    
    /**
     * Get AQI category distribution for pie chart
     */
    public Map<String, Object> getAqiCategoryDistribution(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            List<AqiResponse> data = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (data.isEmpty()) {
                return createErrorResponse("No data available for category analysis");
            }
            
            // Count categories efficiently using collectors
            Map<String, Long> categoryCount = data.stream()
                .collect(Collectors.groupingBy(
                    d -> d.getAqiCategory() != null ? d.getAqiCategory() : "Unknown",
                    Collectors.counting()
                ));
            
            // Convert to percentage
            long total = data.size();
            Map<String, Double> categoryPercentage = categoryCount.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> (entry.getValue() * 100.0) / total
                ));
            
            return Map.of(
                "success", true,
                "city", city,
                "categories", categoryPercentage,
                "totalRecords", total,
                "period", Map.of("start", startDate, "end", endDate)
            );
            
        } catch (Exception e) {
            logger.error("Error calculating category distribution for city {}: {}", city, e.getMessage());
            return createErrorResponse("Failed to analyze categories: " + e.getMessage());
        }
    }
    
    /**
     * Get comparative analytics for multiple cities
     */
    public Map<String, Object> getComparativeAnalytics(List<String> cities, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Process cities in parallel for better performance
            Map<String, CompletableFuture<Map<String, Object>>> cityFutures = cities.stream()
                .collect(Collectors.toMap(
                    city -> city,
                    city -> CompletableFuture.supplyAsync(() -> getAnalyticsStats(city, startDate, endDate))
                ));
            
            // Wait for all cities to complete
            Map<String, Map<String, Object>> cityStats = new HashMap<>();
            cityFutures.forEach((city, future) -> {
                try {
                    cityStats.put(city, future.get());
                } catch (Exception e) {
                    logger.warn("Failed to get stats for city {}: {}", city, e.getMessage());
                    cityStats.put(city, createErrorResponse("Data unavailable"));
                }
            });
            
            return Map.of(
                "success", true,
                "type", "comparative",
                "cities", cityStats,
                "period", Map.of("start", startDate, "end", endDate)
            );
            
        } catch (Exception e) {
            logger.error("Error generating comparative analytics: {}", e.getMessage());
            return createErrorResponse("Failed to generate comparative analytics: " + e.getMessage());
        }
    }
    
    // Private helper methods
    
    private Map<String, Object> processDataForStatistics(List<AqiResponse> data, String city, 
                                                        LocalDateTime startDate, LocalDateTime endDate) {
        
        // Calculate basic statistics efficiently using streams
        OptionalDouble avgAqi = data.stream().mapToInt(AqiResponse::getAqiValue).average();
        OptionalInt maxAqi = data.stream().mapToInt(AqiResponse::getAqiValue).max();
        OptionalInt minAqi = data.stream().mapToInt(AqiResponse::getAqiValue).min();
        
        // Calculate pollutant averages
        OptionalDouble avgPm25 = data.stream()
            .filter(d -> d.getPm25() != null)
            .mapToDouble(AqiResponse::getPm25).average();
        
        OptionalDouble avgPm10 = data.stream()
            .filter(d -> d.getPm10() != null)
            .mapToDouble(AqiResponse::getPm10).average();
        
        // Calculate trend (simple linear regression slope)
        double trend = calculateTrend(data);
        
        // Find most common category
        String mostCommonCategory = data.stream()
            .collect(Collectors.groupingBy(
                d -> d.getAqiCategory() != null ? d.getAqiCategory() : "Unknown",
                Collectors.counting()
            ))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
        
        return Map.of(
            "success", true,
            "city", city,
            "period", Map.of("start", startDate, "end", endDate),
            "totalRecords", data.size(),
            "statistics", Map.of(
                "averageAqi", avgAqi.orElse(0.0),
                "maxAqi", maxAqi.orElse(0),
                "minAqi", minAqi.orElse(0),
                "averagePm25", avgPm25.orElse(0.0),
                "averagePm10", avgPm10.orElse(0.0),
                "trend", trend,
                "mostCommonCategory", mostCommonCategory
            ),
            "healthImpact", calculateHealthImpact(avgAqi.orElse(0.0)),
            "recommendations", generateRecommendations(avgAqi.orElse(0.0), mostCommonCategory)
        );
    }
    
    private Map<String, Map<String, Double>> calculatePollutantStatistics(List<AqiResponse> data) {
        Map<String, Map<String, Double>> stats = new HashMap<>();
        
        // PM2.5 statistics
        List<Double> pm25Values = data.stream()
            .filter(d -> d.getPm25() != null)
            .map(AqiResponse::getPm25)
            .collect(Collectors.toList());
        
        if (!pm25Values.isEmpty()) {
            stats.put("pm25", calculatePollutantStats(pm25Values));
        }
        
        // PM10 statistics
        List<Double> pm10Values = data.stream()
            .filter(d -> d.getPm10() != null)
            .map(AqiResponse::getPm10)
            .collect(Collectors.toList());
        
        if (!pm10Values.isEmpty()) {
            stats.put("pm10", calculatePollutantStats(pm10Values));
        }
        
        // NO2 statistics
        List<Double> no2Values = data.stream()
            .filter(d -> d.getNo2() != null)
            .map(AqiResponse::getNo2)
            .collect(Collectors.toList());
        
        if (!no2Values.isEmpty()) {
            stats.put("no2", calculatePollutantStats(no2Values));
        }
        
        return stats;
    }
    
    private Map<String, Double> calculatePollutantStats(List<Double> values) {
        if (values.isEmpty()) return Map.of();
        
        Collections.sort(values);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = values.get(0);
        double max = values.get(values.size() - 1);
        double median = values.size() % 2 == 0 
            ? (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2.0
            : values.get(values.size() / 2);
        
        return Map.of(
            "average", avg,
            "min", min,
            "max", max,
            "median", median,
            "count", (double) values.size()
        );
    }
    
    private double calculateTrend(List<AqiResponse> data) {
        if (data.size() < 2) return 0.0;
        
        // Simple linear regression to calculate trend
        // Using timestamp as x and AQI as y
        double n = data.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        
        for (int i = 0; i < data.size(); i++) {
            double x = i; // Time index
            double y = data.get(i).getAqiValue();
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        
        // Calculate slope (trend)
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        return slope;
    }
    
    private String calculateHealthImpact(double avgAqi) {
        if (avgAqi <= 50) return "Minimal health impact";
        else if (avgAqi <= 100) return "Acceptable for most people";
        else if (avgAqi <= 150) return "Sensitive groups may be affected";
        else if (avgAqi <= 200) return "Health effects for everyone";
        else if (avgAqi <= 300) return "Serious health effects";
        else return "Emergency conditions";
    }
    
    private List<String> generateRecommendations(double avgAqi, String mostCommonCategory) {
        List<String> recommendations = new ArrayList<>();
        
        if (avgAqi > 100) {
            recommendations.add("Limit outdoor activities during peak hours");
            recommendations.add("Use air purifiers indoors");
            recommendations.add("Wear masks when going outside");
        }
        
        if (avgAqi > 150) {
            recommendations.add("Avoid outdoor exercise");
            recommendations.add("Keep windows closed");
        }
        
        if ("Good".equals(mostCommonCategory)) {
            recommendations.add("Great time for outdoor activities");
            recommendations.add("Air quality is generally healthy");
        }
        
        return recommendations;
    }
    
    private List<AqiResponse> sampleDataForChart(List<AqiResponse> data, int maxPoints) {
        if (data.size() <= maxPoints) {
            return data;
        }
        
        // Sample data evenly
        int step = data.size() / maxPoints;
        List<AqiResponse> sampled = new ArrayList<>();
        
        for (int i = 0; i < data.size(); i += step) {
            sampled.add(data.get(i));
        }
        
        return sampled;
    }
    
    private String buildCacheKey(String city, LocalDateTime startDate, LocalDateTime endDate) {
        return city + "_" + startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + 
               "_" + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
    
    private void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        statsCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().getTimestamp() > CACHE_DURATION_MS);
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        return Map.of(
            "success", false,
            "error", message,
            "timestamp", LocalDateTime.now()
        );
    }
    
    // Inner class for caching
    private static class CachedStats {
        private final Map<String, Object> stats;
        private final long timestamp;
        
        public CachedStats(Map<String, Object> stats) {
            this.stats = stats;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
        
        public Map<String, Object> getStats() {
            return stats;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}