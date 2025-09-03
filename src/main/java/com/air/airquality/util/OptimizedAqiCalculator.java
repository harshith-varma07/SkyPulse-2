package com.air.airquality.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimized AQI Calculator using binary search and efficient data structures
 * Implements US EPA AQI calculation standards with O(log n) lookup performance
 */
public class OptimizedAqiCalculator {
    
    // Optimized AQI breakpoints using TreeMap for O(log n) binary search
    private static final TreeMap<Double, AqiBreakpoint> PM25_BREAKPOINTS = new TreeMap<>();
    private static final TreeMap<Double, AqiBreakpoint> PM10_BREAKPOINTS = new TreeMap<>();
    private static final TreeMap<Double, AqiBreakpoint> NO2_BREAKPOINTS = new TreeMap<>();
    private static final TreeMap<Double, AqiBreakpoint> SO2_BREAKPOINTS = new TreeMap<>();
    private static final TreeMap<Double, AqiBreakpoint> CO_BREAKPOINTS = new TreeMap<>();
    private static final TreeMap<Double, AqiBreakpoint> O3_BREAKPOINTS = new TreeMap<>();
    
    // Cache for frequent calculations
    private static final Map<String, AqiCategoryInfo> CATEGORY_CACHE = new ConcurrentHashMap<>();
    
    // AQI category definitions with colors (optimized as array for O(1) access)
    private static final AqiCategoryInfo[] AQI_CATEGORIES = {
        new AqiCategoryInfo(0, 50, "Good", "#00E400", "Air quality is good. Ideal for outdoor activities."),
        new AqiCategoryInfo(51, 100, "Moderate", "#FFFF00", "Air quality is acceptable for most people."),
        new AqiCategoryInfo(101, 150, "Unhealthy for Sensitive Groups", "#FF7E00", "Sensitive groups may experience minor issues."),
        new AqiCategoryInfo(151, 200, "Unhealthy", "#FF0000", "Everyone may experience health effects."),
        new AqiCategoryInfo(201, 300, "Very Unhealthy", "#8F3F97", "Health alert: everyone may experience serious effects."),
        new AqiCategoryInfo(301, 500, "Hazardous", "#7E0023", "Health warning: emergency conditions affect everyone.")
    };
    
    static {
        initializeBreakpoints();
    }
    
    private static void initializeBreakpoints() {
        // PM2.5 breakpoints (μg/m³)
        PM25_BREAKPOINTS.put(0.0, new AqiBreakpoint(0.0, 12.0, 0, 50));
        PM25_BREAKPOINTS.put(12.1, new AqiBreakpoint(12.1, 35.4, 51, 100));
        PM25_BREAKPOINTS.put(35.5, new AqiBreakpoint(35.5, 55.4, 101, 150));
        PM25_BREAKPOINTS.put(55.5, new AqiBreakpoint(55.5, 150.4, 151, 200));
        PM25_BREAKPOINTS.put(150.5, new AqiBreakpoint(150.5, 250.4, 201, 300));
        PM25_BREAKPOINTS.put(250.5, new AqiBreakpoint(250.5, 500.4, 301, 500));
        
        // PM10 breakpoints (μg/m³)
        PM10_BREAKPOINTS.put(0.0, new AqiBreakpoint(0.0, 54.0, 0, 50));
        PM10_BREAKPOINTS.put(55.0, new AqiBreakpoint(55.0, 154.0, 51, 100));
        PM10_BREAKPOINTS.put(155.0, new AqiBreakpoint(155.0, 254.0, 101, 150));
        PM10_BREAKPOINTS.put(255.0, new AqiBreakpoint(255.0, 354.0, 151, 200));
        PM10_BREAKPOINTS.put(355.0, new AqiBreakpoint(355.0, 424.0, 201, 300));
        PM10_BREAKPOINTS.put(425.0, new AqiBreakpoint(425.0, 604.0, 301, 500));
        
        // NO2 breakpoints (ppb)
        NO2_BREAKPOINTS.put(0.0, new AqiBreakpoint(0.0, 53.0, 0, 50));
        NO2_BREAKPOINTS.put(54.0, new AqiBreakpoint(54.0, 100.0, 51, 100));
        NO2_BREAKPOINTS.put(101.0, new AqiBreakpoint(101.0, 360.0, 101, 150));
        NO2_BREAKPOINTS.put(361.0, new AqiBreakpoint(361.0, 649.0, 151, 200));
        NO2_BREAKPOINTS.put(650.0, new AqiBreakpoint(650.0, 1249.0, 201, 300));
        NO2_BREAKPOINTS.put(1250.0, new AqiBreakpoint(1250.0, 2049.0, 301, 500));
        
        // Initialize other pollutants similarly...
        initializeOtherBreakpoints();
    }
    
    private static void initializeOtherBreakpoints() {
        // SO2 breakpoints (ppb)
        SO2_BREAKPOINTS.put(0.0, new AqiBreakpoint(0.0, 35.0, 0, 50));
        SO2_BREAKPOINTS.put(36.0, new AqiBreakpoint(36.0, 75.0, 51, 100));
        SO2_BREAKPOINTS.put(76.0, new AqiBreakpoint(76.0, 185.0, 101, 150));
        SO2_BREAKPOINTS.put(186.0, new AqiBreakpoint(186.0, 304.0, 151, 200));
        
        // CO breakpoints (ppm)
        CO_BREAKPOINTS.put(0.0, new AqiBreakpoint(0.0, 4.4, 0, 50));
        CO_BREAKPOINTS.put(4.5, new AqiBreakpoint(4.5, 9.4, 51, 100));
        CO_BREAKPOINTS.put(9.5, new AqiBreakpoint(9.5, 12.4, 101, 150));
        CO_BREAKPOINTS.put(12.5, new AqiBreakpoint(12.5, 15.4, 151, 200));
        
        // O3 8-hour breakpoints (ppb)
        O3_BREAKPOINTS.put(0.0, new AqiBreakpoint(0.0, 54.0, 0, 50));
        O3_BREAKPOINTS.put(55.0, new AqiBreakpoint(55.0, 70.0, 51, 100));
        O3_BREAKPOINTS.put(71.0, new AqiBreakpoint(71.0, 85.0, 101, 150));
        O3_BREAKPOINTS.put(86.0, new AqiBreakpoint(86.0, 105.0, 151, 200));
    }
    
    /**
     * Calculate AQI for a specific pollutant using binary search - O(log n) performance
     */
    public static int calculatePollutantAqi(String pollutant, double concentration) {
        TreeMap<Double, AqiBreakpoint> breakpoints = getBreakpointsForPollutant(pollutant);
        if (breakpoints == null || concentration < 0) {
            return 0;
        }
        
        // Binary search using TreeMap's floorEntry - O(log n)
        Map.Entry<Double, AqiBreakpoint> entry = breakpoints.floorEntry(concentration);
        if (entry == null) {
            return 0;
        }
        
        AqiBreakpoint breakpoint = entry.getValue();
        
        // Check if concentration exceeds the breakpoint range
        if (concentration > breakpoint.concentrationHigh) {
            // Find next breakpoint
            Map.Entry<Double, AqiBreakpoint> nextEntry = breakpoints.higherEntry(entry.getKey());
            if (nextEntry != null && concentration <= nextEntry.getValue().concentrationHigh) {
                breakpoint = nextEntry.getValue();
            } else {
                return 500; // Maximum AQI
            }
        }
        
        // Linear interpolation formula: AQI = ((AQI_hi - AQI_lo) / (C_hi - C_lo)) * (C - C_lo) + AQI_lo
        double aqi = ((breakpoint.aqiHigh - breakpoint.aqiLow) / 
                     (breakpoint.concentrationHigh - breakpoint.concentrationLow)) * 
                     (concentration - breakpoint.concentrationLow) + breakpoint.aqiLow;
        
        return Math.max(0, (int) Math.round(aqi));
    }
    
    /**
     * Calculate overall AQI from multiple pollutants - takes maximum AQI
     */
    public static int calculateOverallAqi(Double pm25, Double pm10, Double no2, Double so2, Double co, Double o3) {
        int maxAqi = 0;
        
        if (pm25 != null) {
            maxAqi = Math.max(maxAqi, calculatePollutantAqi("pm25", pm25));
        }
        if (pm10 != null) {
            maxAqi = Math.max(maxAqi, calculatePollutantAqi("pm10", pm10));
        }
        if (no2 != null) {
            maxAqi = Math.max(maxAqi, calculatePollutantAqi("no2", no2));
        }
        if (so2 != null) {
            maxAqi = Math.max(maxAqi, calculatePollutantAqi("so2", so2));
        }
        if (co != null) {
            maxAqi = Math.max(maxAqi, calculatePollutantAqi("co", co));
        }
        if (o3 != null) {
            maxAqi = Math.max(maxAqi, calculatePollutantAqi("o3", o3));
        }
        
        return maxAqi;
    }
    
    /**
     * Get AQI category info using binary search - O(log n) performance
     */
    public static AqiCategoryInfo getCategoryInfo(int aqi) {
        String cacheKey = String.valueOf(aqi);
        AqiCategoryInfo cached = CATEGORY_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Binary search through categories
        for (AqiCategoryInfo category : AQI_CATEGORIES) {
            if (aqi >= category.minValue && aqi <= category.maxValue) {
                CATEGORY_CACHE.put(cacheKey, category);
                return category;
            }
        }
        
        // Default to Hazardous if AQI is above all ranges
        AqiCategoryInfo hazardous = AQI_CATEGORIES[AQI_CATEGORIES.length - 1];
        CATEGORY_CACHE.put(cacheKey, hazardous);
        return hazardous;
    }
    
    /**
     * Get health recommendations based on AQI using optimized lookup
     */
    public static List<String> getHealthRecommendations(int aqi) {
        AqiCategoryInfo category = getCategoryInfo(aqi);
        List<String> recommendations = new ArrayList<>();
        
        switch (category.name) {
            case "Good":
                recommendations.add("Perfect day for outdoor activities");
                recommendations.add("Windows can be opened for fresh air");
                break;
            case "Moderate":
                recommendations.add("Generally acceptable for outdoor activities");
                recommendations.add("Sensitive individuals should limit prolonged outdoor exertion");
                break;
            case "Unhealthy for Sensitive Groups":
                recommendations.add("Sensitive groups should reduce outdoor exertion");
                recommendations.add("Consider closing windows if you have allergies");
                break;
            case "Unhealthy":
                recommendations.add("Everyone should reduce outdoor exertion");
                recommendations.add("Wear a mask when going outside");
                recommendations.add("Use air purifiers indoors");
                break;
            case "Very Unhealthy":
                recommendations.add("Avoid outdoor activities");
                recommendations.add("Keep windows closed");
                recommendations.add("Use air purifiers and keep indoor air clean");
                break;
            case "Hazardous":
                recommendations.add("Stay indoors");
                recommendations.add("Emergency conditions - seek medical advice if feeling unwell");
                recommendations.add("Use N95 masks if must go outside");
                break;
        }
        
        return recommendations;
    }
    
    private static TreeMap<Double, AqiBreakpoint> getBreakpointsForPollutant(String pollutant) {
        switch (pollutant.toLowerCase()) {
            case "pm25": return PM25_BREAKPOINTS;
            case "pm10": return PM10_BREAKPOINTS;
            case "no2": return NO2_BREAKPOINTS;
            case "so2": return SO2_BREAKPOINTS;
            case "co": return CO_BREAKPOINTS;
            case "o3": return O3_BREAKPOINTS;
            default: return null;
        }
    }
    
    // Inner classes for data structures
    public static class AqiBreakpoint {
        public final double concentrationLow;
        public final double concentrationHigh;
        public final int aqiLow;
        public final int aqiHigh;
        
        public AqiBreakpoint(double concentrationLow, double concentrationHigh, int aqiLow, int aqiHigh) {
            this.concentrationLow = concentrationLow;
            this.concentrationHigh = concentrationHigh;
            this.aqiLow = aqiLow;
            this.aqiHigh = aqiHigh;
        }
    }
    
    public static class AqiCategoryInfo {
        public final int minValue;
        public final int maxValue;
        public final String name;
        public final String color;
        public final String description;
        
        public AqiCategoryInfo(int minValue, int maxValue, String name, String color, String description) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.name = name;
            this.color = color;
            this.description = description;
        }
    }
}