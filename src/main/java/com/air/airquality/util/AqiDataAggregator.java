package com.air.airquality.util;

import com.air.airquality.model.AqiData;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * High-performance data aggregator using TreeMap and efficient algorithms
 * for real-time analytics processing
 */
public class AqiDataAggregator {
    
    // Use TreeMap for sorted time-series data - O(log n) operations
    private final NavigableMap<LocalDateTime, List<AqiData>> timeSeriesData;
    
    // Pre-computed statistics cache
    private volatile AggregatedStatistics cachedStats;
    private volatile LocalDateTime lastComputeTime;
    private static final long CACHE_VALIDITY_MINUTES = 5;
    
    public AqiDataAggregator() {
        // ConcurrentSkipListMap for thread-safe sorted operations
        this.timeSeriesData = new ConcurrentSkipListMap<>();
    }
    
    /**
     * Add data point with O(log n) insertion
     */
    public void addDataPoint(AqiData data) {
        timeSeriesData.computeIfAbsent(data.getTimestamp(), k -> new ArrayList<>()).add(data);
        invalidateCache();
    }
    
    /**
     * Bulk insert with optimized batch processing
     */
    public void addDataPoints(Collection<AqiData> dataPoints) {
        // Group by timestamp for efficient insertion
        Map<LocalDateTime, List<AqiData>> groupedData = dataPoints.stream()
            .collect(Collectors.groupingBy(AqiData::getTimestamp));
        
        groupedData.forEach((timestamp, dataList) -> 
            timeSeriesData.computeIfAbsent(timestamp, k -> new ArrayList<>()).addAll(dataList));
        
        invalidateCache();
    }
    
    /**
     * Get data within time range using efficient range query
     */
    public List<AqiData> getDataInRange(LocalDateTime start, LocalDateTime end) {
        return timeSeriesData.subMap(start, true, end, true)
            .values()
            .stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }
    
    /**
     * Get aggregated statistics with caching
     */
    public AggregatedStatistics getStatistics(String city, LocalDateTime start, LocalDateTime end) {
        // Check cache validity
        if (isCacheValid()) {
            return cachedStats;
        }
        
        return computeStatistics(city, start, end);
    }
    
    /**
     * Compute statistics using efficient streaming algorithms
     */
    private AggregatedStatistics computeStatistics(String city, LocalDateTime start, LocalDateTime end) {
        List<AqiData> data = getDataInRange(start, end)
            .stream()
            .filter(d -> city == null || city.equals(d.getCity()))
            .collect(Collectors.toList());
        
        if (data.isEmpty()) {
            return new AggregatedStatistics();
        }
        
        // Use streaming for memory-efficient computation
        IntSummaryStatistics aqiStats = data.stream()
            .mapToInt(AqiData::getAqiValue)
            .summaryStatistics();
        
        DoubleSummaryStatistics pm25Stats = data.stream()
            .filter(d -> d.getPm25() != null)
            .mapToDouble(AqiData::getPm25)
            .summaryStatistics();
        
        DoubleSummaryStatistics pm10Stats = data.stream()
            .filter(d -> d.getPm10() != null)
            .mapToDouble(AqiData::getPm10)
            .summaryStatistics();
        
        // Calculate AQI distribution using counting sort for efficiency
        int[] aqiDistribution = new int[6]; // Good, Moderate, Unhealthy, VeryUnhealthy, Hazardous, Extreme
        data.forEach(d -> {
            int aqi = d.getAqiValue();
            if (aqi <= 50) aqiDistribution[0]++;
            else if (aqi <= 100) aqiDistribution[1]++;
            else if (aqi <= 150) aqiDistribution[2]++;
            else if (aqi <= 200) aqiDistribution[3]++;
            else if (aqi <= 300) aqiDistribution[4]++;
            else aqiDistribution[5]++;
        });
        
        AggregatedStatistics stats = new AggregatedStatistics(
            data.size(),
            aqiStats.getAverage(),
            aqiStats.getMin(),
            aqiStats.getMax(),
            pm25Stats.getAverage(),
            pm10Stats.getAverage(),
            aqiDistribution
        );
        
        // Cache the results
        cachedStats = stats;
        lastComputeTime = LocalDateTime.now();
        
        return stats;
    }
    
    /**
     * Get trending data using sliding window algorithm
     */
    public List<TrendPoint> getTrendData(String city, LocalDateTime start, LocalDateTime end, int windowSize) {
        List<AqiData> data = getDataInRange(start, end)
            .stream()
            .filter(d -> city == null || city.equals(d.getCity()))
            .sorted(Comparator.comparing(AqiData::getTimestamp))
            .collect(Collectors.toList());
        
        List<TrendPoint> trends = new ArrayList<>();
        
        // Sliding window for trend calculation
        for (int i = 0; i <= data.size() - windowSize; i++) {
            List<AqiData> window = data.subList(i, i + windowSize);
            double avgAqi = window.stream().mapToInt(AqiData::getAqiValue).average().orElse(0);
            LocalDateTime timestamp = window.get(window.size() / 2).getTimestamp();
            trends.add(new TrendPoint(timestamp, avgAqi));
        }
        
        return trends;
    }
    
    /**
     * Clean old data with efficient range deletion
     */
    public void cleanupOldData(LocalDateTime cutoffDate) {
        NavigableMap<LocalDateTime, List<AqiData>> oldData = timeSeriesData.headMap(cutoffDate, false);
        oldData.clear();
        invalidateCache();
    }
    
    private boolean isCacheValid() {
        return cachedStats != null && lastComputeTime != null &&
               lastComputeTime.isAfter(LocalDateTime.now().minusMinutes(CACHE_VALIDITY_MINUTES));
    }
    
    private void invalidateCache() {
        cachedStats = null;
        lastComputeTime = null;
    }
    
    // Data classes for structured results
    public static class AggregatedStatistics {
        private final long totalRecords;
        private final double avgAqi;
        private final double minAqi;
        private final double maxAqi;
        private final double avgPm25;
        private final double avgPm10;
        private final int[] aqiDistribution;
        
        public AggregatedStatistics() {
            this(0, 0, 0, 0, 0, 0, new int[6]);
        }
        
        public AggregatedStatistics(long totalRecords, double avgAqi, double minAqi, double maxAqi,
                                   double avgPm25, double avgPm10, int[] aqiDistribution) {
            this.totalRecords = totalRecords;
            this.avgAqi = avgAqi;
            this.minAqi = minAqi;
            this.maxAqi = maxAqi;
            this.avgPm25 = avgPm25;
            this.avgPm10 = avgPm10;
            this.aqiDistribution = aqiDistribution.clone();
        }
        
        // Getters
        public long getTotalRecords() { return totalRecords; }
        public double getAvgAqi() { return avgAqi; }
        public double getMinAqi() { return minAqi; }
        public double getMaxAqi() { return maxAqi; }
        public double getAvgPm25() { return avgPm25; }
        public double getAvgPm10() { return avgPm10; }
        public int[] getAqiDistribution() { return aqiDistribution.clone(); }
    }
    
    public static class TrendPoint {
        private final LocalDateTime timestamp;
        private final double value;
        
        public TrendPoint(LocalDateTime timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getValue() { return value; }
    }
}
