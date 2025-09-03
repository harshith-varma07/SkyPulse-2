package com.air.airquality.services;

import com.air.airquality.dto.AqiResponse;
import com.air.airquality.model.AqiData;
import com.air.airquality.repository.AqiDataRepository;
import com.air.airquality.util.AqiDataAggregator;
import com.air.airquality.util.LRUCacheWithTTL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Optimized Analytics Service using efficient data structures and algorithms
 * for high-performance real-time analytics processing
 */
@Service
public class OptimizedAnalyticsService {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedAnalyticsService.class);
    
    @Autowired
    private AqiDataRepository aqiDataRepository;
    
    // High-performance data aggregator
    private AqiDataAggregator dataAggregator;
    
    // LRU Cache for analytics results with 15-minute TTL
    private LRUCacheWithTTL<String, AnalyticsResult> analyticsCache;
    
    // Thread pool for parallel analytics computation
    private ExecutorService analyticsExecutor;
    
    // Background data refresh service
    private ScheduledExecutorService dataRefreshService;
    
    @PostConstruct
    public void init() {
        // Initialize data aggregator
        dataAggregator = new AqiDataAggregator();
        
        // Initialize analytics cache (15 minute TTL, 500 max entries)
        analyticsCache = new LRUCacheWithTTL<>(500, 900000);
        
        // Initialize thread pool for analytics computation
        analyticsExecutor = new ThreadPoolExecutor(
            4, // core threads
            16, // max threads
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Analytics-Worker-" + counter++);
                    t.setDaemon(true);
                    return t;
                }
            }
        );
        
        // Background data refresh every 30 minutes
        dataRefreshService = Executors.newSingleThreadScheduledExecutor();
        dataRefreshService.scheduleAtFixedRate(this::refreshAggregatorData, 0, 30, TimeUnit.MINUTES);
    }
    
    /**
     * Get comprehensive analytics for a city within date range
     * Uses caching and parallel processing for optimal performance
     */
    public CompletableFuture<AnalyticsResult> getAnalytics(String city, LocalDateTime startDate, LocalDateTime endDate) {
        String cacheKey = generateCacheKey(city, startDate, endDate);
        
        // Check cache first
        AnalyticsResult cached = analyticsCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Analytics cache hit for city: {}", city);
            return CompletableFuture.completedFuture(cached);
        }
        
        // Compute analytics asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                return computeAnalytics(city, startDate, endDate, cacheKey);
            } catch (Exception e) {
                logger.error("Error computing analytics for city {}: {}", city, e.getMessage(), e);
                return new AnalyticsResult(); // Return empty result on error
            }
        }, analyticsExecutor);
    }
    
    /**
     * Compute analytics using efficient data structures and algorithms
     */
    private AnalyticsResult computeAnalytics(String city, LocalDateTime startDate, LocalDateTime endDate, String cacheKey) {
        logger.info("Computing analytics for city: {} from {} to {}", city, startDate, endDate);
        
        // Get aggregated statistics
        AqiDataAggregator.AggregatedStatistics stats = dataAggregator.getStatistics(city, startDate, endDate);
        
        // Parallel computation of different analytics components
        List<CompletableFuture<Void>> computationTasks = new ArrayList<>();
        AnalyticsResult result = new AnalyticsResult();
        
        // Task 1: Basic statistics
        CompletableFuture<Void> statsTask = CompletableFuture.runAsync(() -> {
            result.setTotalRecords(stats.getTotalRecords());
            result.setAvgAqi(stats.getAvgAqi());
            result.setMinAqi(stats.getMinAqi());
            result.setMaxAqi(stats.getMaxAqi());
            result.setAvgPm25(stats.getAvgPm25());
            result.setAvgPm10(stats.getAvgPm10());
        });
        computationTasks.add(statsTask);
        
        // Task 2: Trend analysis
        CompletableFuture<Void> trendTask = CompletableFuture.runAsync(() -> {
            List<AqiDataAggregator.TrendPoint> trends = dataAggregator.getTrendData(city, startDate, endDate, 5);
            result.setTrendData(trends);
        });
        computationTasks.add(trendTask);
        
        // Task 3: AQI distribution
        CompletableFuture<Void> distributionTask = CompletableFuture.runAsync(() -> {
            result.setAqiDistribution(stats.getAqiDistribution());
        });
        computationTasks.add(distributionTask);
        
        // Task 4: Time series data for charts
        CompletableFuture<Void> timeSeriesTask = CompletableFuture.runAsync(() -> {
            List<AqiData> timeSeriesData = dataAggregator.getDataInRange(startDate, endDate)
                .stream()
                .filter(d -> city == null || city.equals(d.getCity()))
                .sorted(Comparator.comparing(AqiData::getTimestamp))
                .collect(Collectors.toList());
            
            // Sample data if too large (keep every nth point for performance)
            if (timeSeriesData.size() > 1000) {
                int step = timeSeriesData.size() / 1000;
                timeSeriesData = timeSeriesData.stream()
                    .collect(ArrayList::new, (list, item) -> {
                        if (list.size() % step == 0) list.add(item);
                    }, ArrayList::addAll);
            }
            
            result.setTimeSeriesData(timeSeriesData);
        });
        computationTasks.add(timeSeriesTask);
        
        // Wait for all tasks to complete
        CompletableFuture.allOf(computationTasks.toArray(new CompletableFuture[0])).join();
        
        // Cache the result
        analyticsCache.put(cacheKey, result);
        
        logger.info("Analytics computation completed for city: {}, records: {}", city, result.getTotalRecords());
        return result;
    }
    
    /**
     * Get real-time analytics dashboard data
     * Optimized for quick response times
     */
    public Map<String, Object> getRealTimeDashboard(List<String> cities) {
        Map<String, Object> dashboard = new ConcurrentHashMap<>();
        
        // Parallel processing for multiple cities
        List<CompletableFuture<Void>> cityTasks = cities.stream()
            .map(city -> CompletableFuture.runAsync(() -> {
                try {
                    // Get latest data for city
                    Optional<AqiData> latestData = aqiDataRepository.findTopByCityOrderByTimestampDesc(city);
                    if (latestData.isPresent()) {
                        Map<String, Object> cityInfo = new HashMap<>();
                        AqiData data = latestData.get();
                        cityInfo.put("aqi", data.getAqiValue());
                        cityInfo.put("pm25", data.getPm25());
                        cityInfo.put("pm10", data.getPm10());
                        cityInfo.put("timestamp", data.getTimestamp());
                        cityInfo.put("category", getAqiCategory(data.getAqiValue()));
                        dashboard.put(city, cityInfo);
                    }
                } catch (Exception e) {
                    logger.error("Error getting dashboard data for city {}: {}", city, e.getMessage());
                }
            }, analyticsExecutor))
            .collect(Collectors.toList());
        
        // Wait for all city data with timeout
        try {
            CompletableFuture.allOf(cityTasks.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Some dashboard data requests timed out: {}", e.getMessage());
        }
        
        return dashboard;
    }
    
    /**
     * Refresh aggregator data in background
     */
    private void refreshAggregatorData() {
        try {
            logger.info("Refreshing aggregator data...");
            LocalDateTime cutoff = LocalDateTime.now().minusMonths(18);
            
            // Clean old data from aggregator
            dataAggregator.cleanupOldData(cutoff);
            
            // Load recent data (last 7 days) for hot cache
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<AqiData> recentData = aqiDataRepository.findByCityAndTimestampBetween("", sevenDaysAgo, LocalDateTime.now());
            
            // Bulk insert into aggregator
            if (!recentData.isEmpty()) {
                dataAggregator.addDataPoints(recentData);
                logger.info("Loaded {} recent data points into aggregator", recentData.size());
            }
            
        } catch (Exception e) {
            logger.error("Error refreshing aggregator data: {}", e.getMessage(), e);
        }
    }
    
    private String generateCacheKey(String city, LocalDateTime start, LocalDateTime end) {
        return String.format("%s_%s_%s", 
            city != null ? city : "ALL", 
            start.toString(), 
            end.toString());
    }
    
    private String getAqiCategory(Integer aqi) {
        if (aqi <= 50) return "Good";
        else if (aqi <= 100) return "Moderate";
        else if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        else if (aqi <= 200) return "Unhealthy";
        else if (aqi <= 300) return "Very Unhealthy";
        else return "Hazardous";
    }
    
    // Analytics result data class
    public static class AnalyticsResult {
        private long totalRecords;
        private double avgAqi;
        private double minAqi;
        private double maxAqi;
        private double avgPm25;
        private double avgPm10;
        private List<AqiDataAggregator.TrendPoint> trendData;
        private int[] aqiDistribution;
        private List<AqiData> timeSeriesData;
        
        // Constructors, getters, and setters
        public AnalyticsResult() {
            this.aqiDistribution = new int[6];
            this.trendData = new ArrayList<>();
            this.timeSeriesData = new ArrayList<>();
        }
        
        // Getters and setters
        public long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
        
        public double getAvgAqi() { return avgAqi; }
        public void setAvgAqi(double avgAqi) { this.avgAqi = avgAqi; }
        
        public double getMinAqi() { return minAqi; }
        public void setMinAqi(double minAqi) { this.minAqi = minAqi; }
        
        public double getMaxAqi() { return maxAqi; }
        public void setMaxAqi(double maxAqi) { this.maxAqi = maxAqi; }
        
        public double getAvgPm25() { return avgPm25; }
        public void setAvgPm25(double avgPm25) { this.avgPm25 = avgPm25; }
        
        public double getAvgPm10() { return avgPm10; }
        public void setAvgPm10(double avgPm10) { this.avgPm10 = avgPm10; }
        
        public List<AqiDataAggregator.TrendPoint> getTrendData() { return trendData; }
        public void setTrendData(List<AqiDataAggregator.TrendPoint> trendData) { this.trendData = trendData; }
        
        public int[] getAqiDistribution() { return aqiDistribution; }
        public void setAqiDistribution(int[] aqiDistribution) { this.aqiDistribution = aqiDistribution; }
        
        public List<AqiData> getTimeSeriesData() { return timeSeriesData; }
        public void setTimeSeriesData(List<AqiData> timeSeriesData) { this.timeSeriesData = timeSeriesData; }
    }
}
