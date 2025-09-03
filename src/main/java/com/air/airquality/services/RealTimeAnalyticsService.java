package com.air.airquality.services;

import com.air.airquality.dto.OpenAQResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RealTimeAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeAnalyticsService.class);
    
    @Value("${openaq.api.url:https://api.openaq.org/v2/measurements}")
    private String openAQMeasurementsUrl;
    
    @Value("${python.analytics.script.path:python-analytics/analytics_service.py}")
    private String pythonScriptPath;
    
    @Value("${python.executable:python}")
    private String pythonExecutable;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetch real-time data from OpenAQ API for specified city and time period
     */
    public Map<String, Object> fetchRealTimeData(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            logger.info("Fetching real-time data for city: {} from {} to {}", city, startDate, endDate);
            
            // Build OpenAQ API URL with parameters
            String url = buildOpenAQUrl(city, startDate, endDate);
            logger.debug("OpenAQ API URL: {}", url);
            
            // Fetch data from OpenAQ API
            ResponseEntity<OpenAQResponse> response = restTemplate.getForEntity(url, OpenAQResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                OpenAQResponse apiResponse = response.getBody();
                List<Map<String, Object>> processedData = processOpenAQResponse(apiResponse, city);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("city", city);
                result.put("startDate", startDate);
                result.put("endDate", endDate);
                result.put("data", processedData);
                result.put("count", processedData.size());
                result.put("source", "OpenAQ API Real-time");
                
                return result;
            } else {
                logger.warn("OpenAQ API returned non-success status: {} for city: {}", 
                           response.getStatusCode(), city);
                return createFallbackResponse(city, startDate, endDate);
            }
            
        } catch (Exception e) {
            logger.error("Error fetching real-time data for city {}: {}", city, e.getMessage());
            return createFallbackResponse(city, startDate, endDate);
        }
    }

    /**
     * Generate analytics using Python service
     */
    public Map<String, Object> generateAnalytics(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Step 1: Fetch real-time data
            Map<String, Object> dataResponse = fetchRealTimeData(city, startDate, endDate);
            
            if (!Boolean.TRUE.equals(dataResponse.get("success"))) {
                return dataResponse; // Return error response
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) dataResponse.get("data");
            
            if (data.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "No data available for the specified time period");
                return result;
            }

            // Step 2: Convert data to JSON for Python processing
            String dataJson = objectMapper.writeValueAsString(data);

            // Step 3: Call Python analytics service
            Map<String, Object> analyticsResult = callPythonAnalytics(dataJson, city, startDate, endDate);

            return analyticsResult;

        } catch (Exception e) {
            logger.error("Error generating analytics for city {}: {}", city, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to generate analytics: " + e.getMessage());
            return result;
        }
    }

    /**
     * Generate analytics charts using Python
     */
    public Map<String, Object> generateCharts(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Fetch real-time data
            Map<String, Object> dataResponse = fetchRealTimeData(city, startDate, endDate);
            
            if (!Boolean.TRUE.equals(dataResponse.get("success"))) {
                return dataResponse;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) dataResponse.get("data");
            String dataJson = objectMapper.writeValueAsString(data);

            // Generate different types of charts
            Map<String, String> charts = new HashMap<>();
            
            // Generate charts in parallel for better performance
            CompletableFuture<String> trendChart = generateChartAsync(dataJson, city, "trend_chart");
            CompletableFuture<String> barChart = generateChartAsync(dataJson, city, "bar_chart");
            CompletableFuture<String> pieChart = generateChartAsync(dataJson, city, "pie_chart");
            CompletableFuture<String> distChart = generateChartAsync(dataJson, city, "dist_chart");

            // Wait for all charts to complete (with timeout)
            CompletableFuture.allOf(trendChart, barChart, pieChart, distChart)
                             .get(60, TimeUnit.SECONDS);

            charts.put("trendChart", trendChart.get());
            charts.put("barChart", barChart.get());
            charts.put("pieChart", pieChart.get());
            charts.put("distributionChart", distChart.get());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("city", city);
            result.put("charts", charts);
            result.put("dataPoints", data.size());

            return result;

        } catch (Exception e) {
            logger.error("Error generating charts for city {}: {}", city, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to generate charts: " + e.getMessage());
            return result;
        }
    }

    /**
     * Generate PDF report using Python analytics
     */
    public byte[] generatePDFReport(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Fetch real-time data
            Map<String, Object> dataResponse = fetchRealTimeData(city, startDate, endDate);
            
            if (!Boolean.TRUE.equals(dataResponse.get("success"))) {
                throw new RuntimeException("Failed to fetch data for PDF generation");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) dataResponse.get("data");
            String dataJson = objectMapper.writeValueAsString(data);

            // Call Python service to generate PDF
            List<String> command = Arrays.asList(
                pythonExecutable, 
                pythonScriptPath, 
                dataJson, 
                city, 
                "pdf",
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            
            // Read PDF data from stdout
            byte[] pdfData;
            try (InputStream inputStream = process.getInputStream()) {
                pdfData = inputStream.readAllBytes();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python script failed with exit code: " + exitCode);
            }

            return pdfData;

        } catch (Exception e) {
            logger.error("Error generating PDF for city {}: {}", city, e.getMessage());
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage());
        }
    }

    /**
     * Get analytics statistics using Python
     */
    public Map<String, Object> getAnalyticsStats(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Fetch real-time data
            Map<String, Object> dataResponse = fetchRealTimeData(city, startDate, endDate);
            
            if (!Boolean.TRUE.equals(dataResponse.get("success"))) {
                return dataResponse;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) dataResponse.get("data");
            String dataJson = objectMapper.writeValueAsString(data);

            // Call Python service for statistics
            String statsJson = callPythonScript(dataJson, city, "stats");
            Map<String, Object> stats = objectMapper.readValue(statsJson, Map.class);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("city", city);
            result.put("statistics", stats);
            result.put("dataPoints", data.size());

            return result;

        } catch (Exception e) {
            logger.error("Error getting analytics stats for city {}: {}", city, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Failed to get analytics statistics: " + e.getMessage());
            return result;
        }
    }

    // Private helper methods
    
    private String buildOpenAQUrl(String city, LocalDateTime startDate, LocalDateTime endDate) {
        StringBuilder url = new StringBuilder(openAQMeasurementsUrl);
        url.append("?city=").append(city.replace(" ", "%20"));
        url.append("&date_from=").append(startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        url.append("&date_to=").append(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        url.append("&parameter=pm25,pm10,no2,so2,co,o3");
        url.append("&limit=10000"); // Get more data for analytics
        url.append("&sort=desc");
        
        return url.toString();
    }

    private List<Map<String, Object>> processOpenAQResponse(OpenAQResponse response, String city) {
        List<Map<String, Object>> processedData = new ArrayList<>();

        if (response.getResults() == null) {
            return processedData;
        }

        // Group measurements by timestamp for better data structure
        Map<String, Map<String, Object>> groupedData = new HashMap<>();

        for (OpenAQResponse.OpenAQResult result : response.getResults()) {
            for (OpenAQResponse.Measurement measurement : result.getMeasurements()) {
                String timestamp = ((Map<String, Object>) measurement.getDate()).get("utc").toString();
                groupedData.computeIfAbsent(timestamp, k -> {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("city", city);
                    dataPoint.put("timestamp", timestamp);
                    dataPoint.put("aqiValue", 0); // Will be calculated
                    return dataPoint;
                });

                Map<String, Object> dataPoint = groupedData.get(timestamp);
                String parameter = measurement.getParameter().toLowerCase();
                dataPoint.put(parameter, measurement.getValue());
            }
        }

        // Calculate AQI and add to processed data
        for (Map<String, Object> dataPoint : groupedData.values()) {
            // Calculate AQI based on PM2.5 if available
            Double pm25 = (Double) dataPoint.get("pm25");
            if (pm25 != null) {
                int aqi = calculateAQI(pm25);
                dataPoint.put("aqiValue", aqi);
            } else {
                dataPoint.put("aqiValue", 50); // Default moderate value
            }
            
            processedData.add(dataPoint);
        }

        // Sort by timestamp (newest first)
        processedData.sort((a, b) -> {
            String timeA = (String) a.get("timestamp");
            String timeB = (String) b.get("timestamp");
            return timeB.compareTo(timeA);
        });

        return processedData;
    }

    private Map<String, Object> createFallbackResponse(String city, LocalDateTime startDate, LocalDateTime endDate) {
        // Generate fallback data for analytics when API fails
        List<Map<String, Object>> fallbackData = generateFallbackData(city, startDate, endDate);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("city", city);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("data", fallbackData);
        result.put("count", fallbackData.size());
        result.put("source", "Fallback Simulated Data");
        result.put("note", "Using simulated data due to API unavailability");
        
        return result;
    }

    private List<Map<String, Object>> generateFallbackData(String city, LocalDateTime startDate, LocalDateTime endDate) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        // Generate data points every 6 hours
        LocalDateTime current = startDate;
        Random random = new Random();
        
        // Base AQI for different cities
        int baseAqi = getBaseAQI(city);
        
        while (current.isBefore(endDate)) {
            Map<String, Object> dataPoint = new HashMap<>();
            
            // Add some realistic variation
            double variation = 0.8 + (random.nextDouble() * 0.4); // 80% to 120% variation
            int aqi = Math.max(1, (int) (baseAqi * variation));
            
            dataPoint.put("city", city);
            dataPoint.put("timestamp", current.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            dataPoint.put("aqiValue", aqi);
            
            // Generate realistic pollutant values based on AQI
            dataPoint.put("pm25", aqi * 0.6);
            dataPoint.put("pm10", aqi * 0.9);
            dataPoint.put("no2", aqi * 0.4);
            dataPoint.put("so2", aqi * 0.2);
            dataPoint.put("co", aqi * 0.03);
            dataPoint.put("o3", aqi * 0.5);
            
            data.add(dataPoint);
            current = current.plusHours(6);
        }
        
        return data;
    }

    private int getBaseAQI(String city) {
        // Base AQI values for different cities
        Map<String, Integer> cityAqi = new HashMap<>();
        cityAqi.put("Delhi", 150);
        cityAqi.put("Mumbai", 90);
        cityAqi.put("Bangalore", 70);
        cityAqi.put("Chennai", 80);
        cityAqi.put("Kolkata", 135);
        cityAqi.put("New York", 45);
        cityAqi.put("London", 50);
        cityAqi.put("Paris", 60);
        cityAqi.put("Beijing", 180);
        
        return cityAqi.getOrDefault(city, 75);
    }

    private int calculateAQI(double pm25) {
        // EPA AQI calculation for PM2.5
        double[] breakpoints = {0, 12.0, 35.4, 55.4, 150.4, 250.4, 350.4, 500.4};
        int[] aqiValues = {0, 50, 100, 150, 200, 300, 400, 500};
        
        for (int i = 0; i < breakpoints.length - 1; i++) {
            if (pm25 >= breakpoints[i] && pm25 <= breakpoints[i + 1]) {
                double aqiRange = aqiValues[i + 1] - aqiValues[i];
                double concentrationRange = breakpoints[i + 1] - breakpoints[i];
                double ratio = (pm25 - breakpoints[i]) / concentrationRange;
                return (int) Math.round(aqiValues[i] + (ratio * aqiRange));
            }
        }
        return 500; // Maximum AQI
    }

    private Map<String, Object> callPythonAnalytics(String dataJson, String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Get statistics
            String statsJson = callPythonScript(dataJson, city, "stats");
            Map<String, Object> stats = objectMapper.readValue(statsJson, Map.class);

            // Generate charts
            Map<String, String> charts = new HashMap<>();
            charts.put("trendChart", callPythonScript(dataJson, city, "trend_chart"));
            charts.put("barChart", callPythonScript(dataJson, city, "bar_chart"));
            charts.put("pieChart", callPythonScript(dataJson, city, "pie_chart"));
            charts.put("distributionChart", callPythonScript(dataJson, city, "dist_chart"));

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("city", city);
            result.put("startDate", startDate);
            result.put("endDate", endDate);
            result.put("statistics", stats);
            result.put("charts", charts);

            return result;

        } catch (Exception e) {
            logger.error("Error calling Python analytics: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Python analytics processing failed: " + e.getMessage());
            return result;
        }
    }

    private String callPythonScript(String dataJson, String city, String operation) throws Exception {
        List<String> command = Arrays.asList(
            pythonExecutable, 
            pythonScriptPath, 
            dataJson, 
            city, 
            operation
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Python script failed with exit code: " + exitCode + 
                                     ", output: " + output.toString());
        }

        return output.toString().trim();
    }

    private CompletableFuture<String> generateChartAsync(String dataJson, String city, String operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callPythonScript(dataJson, city, operation);
            } catch (Exception e) {
                logger.error("Error generating chart {}: {}", operation, e.getMessage());
                return ""; // Return empty string on error
            }
        });
    }
}
