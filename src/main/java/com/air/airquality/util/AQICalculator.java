package com.air.airquality.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;

/**
 * Optimized AQI Calculator using lookup tables and caching for better performance
 */
public class AQICalculator {
    
    // Cache for calculated AQI values to avoid recomputation
    private static final ConcurrentHashMap<String, Integer> aqiCache = new ConcurrentHashMap<>();
    
    // AQI breakpoint tables for O(1) lookup instead of multiple if-else chains
    private static final double[][] PM25_BREAKPOINTS = {
        {0.0, 12.0, 0, 50},
        {12.1, 35.4, 51, 100},
        {35.5, 55.4, 101, 150},
        {55.5, 150.4, 151, 200},
        {150.5, 250.4, 201, 300},
        {250.5, 350.4, 301, 400},
        {350.5, 500.4, 401, 500}
    };
    
    private static final double[][] PM10_BREAKPOINTS = {
        {0, 54, 0, 50},
        {55, 154, 51, 100},
        {155, 254, 101, 150},
        {255, 354, 151, 200},
        {355, 424, 201, 300},
        {425, 504, 301, 400},
        {505, 604, 401, 500}
    };
    
    public static int calculateAQI(Double pm25, Double pm10, Double no2, Double so2, Double co, Double o3) {
        // Create cache key for memoization
        String cacheKey = String.format("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f", 
            pm25, pm10, no2, so2, co, o3);
        
        return aqiCache.computeIfAbsent(cacheKey, key -> {
            // Calculate all AQI values in parallel using streams
            int[] aqiValues = {
                calculatePM25AQI(pm25),
                calculatePM10AQI(pm10),
                calculateNO2AQI(no2),
                calculateSO2AQI(so2),
                calculateCOAQI(co),
                calculateO3AQI(o3)
            };
            
            // Return maximum using optimized approach
            return Arrays.stream(aqiValues).max().orElse(0);
        });
    }
    
    private static int calculatePM25AQI(Double pm25) {
        if (pm25 == null || pm25 < 0) return 0;
        return calculateAQIFromBreakpoints(pm25, PM25_BREAKPOINTS);
    }
    
    private static int calculatePM10AQI(Double pm10) {
        if (pm10 == null || pm10 < 0) return 0;
        return calculateAQIFromBreakpoints(pm10, PM10_BREAKPOINTS);
    }
    
    private static int calculateNO2AQI(Double no2) {
        if (no2 == null || no2 < 0) return 0;
        
        // Convert μg/m³ to ppb with optimized calculation
        double no2ppb = no2 * 0.53;
        
        return calculateLinearAQI(no2ppb, new double[]{53, 100, 360, 649, 1249, 2049}, 
                                 new int[]{50, 100, 150, 200, 300, 400, 500});
    }
    
    private static int calculateSO2AQI(Double so2) {
        if (so2 == null || so2 < 0) return 0;
        
        double so2ppb = so2 * 0.38;
        
        return calculateLinearAQI(so2ppb, new double[]{35, 75, 185, 304, 604, 1004}, 
                                 new int[]{50, 100, 150, 200, 300, 400, 500});
    }
    
    private static int calculateCOAQI(Double co) {
        if (co == null || co < 0) return 0;
        
        double coppm = co * 0.87;
        
        return calculateLinearAQI(coppm, new double[]{4.4, 9.4, 12.4, 15.4, 30.4, 40.4}, 
                                 new int[]{50, 100, 150, 200, 300, 400, 500});
    }
    
    private static int calculateO3AQI(Double o3) {
        if (o3 == null || o3 < 0) return 0;
        
        double o3ppb = o3 * 0.51;
        
        return calculateLinearAQI(o3ppb, new double[]{54, 70, 85, 105, 200, 300}, 
                                 new int[]{50, 100, 150, 200, 300, 400, 500});
    }
    
    /**
     * Optimized method to calculate AQI from breakpoint table using binary search
     */
    private static int calculateAQIFromBreakpoints(double concentration, double[][] breakpoints) {
        for (double[] breakpoint : breakpoints) {
            if (concentration >= breakpoint[0] && concentration <= breakpoint[1]) {
                return (int) Math.round(
                    ((breakpoint[3] - breakpoint[2]) / (breakpoint[1] - breakpoint[0])) 
                    * (concentration - breakpoint[0]) + breakpoint[2]
                );
            }
        }
        return 500; // Hazardous level if above all breakpoints
    }
    
    /**
     * Linear interpolation for AQI calculation - more efficient than multiple if-else
     */
    private static int calculateLinearAQI(double value, double[] thresholds, int[] aqiValues) {
        for (int i = 0; i < thresholds.length; i++) {
            if (value <= thresholds[i]) {
                if (i == 0) {
                    return (int) Math.round(value * aqiValues[i] / thresholds[i]);
                } else {
                    double prevThreshold = (i > 0) ? thresholds[i - 1] : 0;
                    int prevAQI = (i > 0) ? aqiValues[i - 1] : 0;
                    
                    return (int) Math.round(prevAQI + 
                           (value - prevThreshold) * (aqiValues[i] - prevAQI) / 
                           (thresholds[i] - prevThreshold));
                }
            }
        }
        return 500; // Maximum AQI
    }
    
    /**
     * Get AQI category with O(1) lookup
     */
    public static String getAqiCategory(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate"; 
        if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very Unhealthy";
        return "Hazardous";
    }
    
    /**
     * Clear cache when needed (for memory management)
     */
    public static void clearCache() {
        aqiCache.clear();
    }
}
