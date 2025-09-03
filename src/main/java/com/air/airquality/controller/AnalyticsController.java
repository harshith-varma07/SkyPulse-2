package com.air.airquality.controller;

import com.air.airquality.dto.AqiResponse;
import com.air.airquality.services.AqiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/export")
public class AnalyticsController {

    @Autowired
    private AqiService aqiService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PYTHON_SCRIPT_PATH = "python-analytics/analytics_service.py";
    private static final String PYTHON_EXECUTABLE = "python"; // or "python3" on some systems

    @GetMapping("/analytics-pdf")
    public ResponseEntity<byte[]> exportAnalyticsPDF(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Analytics PDF export requires user authentication".getBytes());
            }

            // Set default dates if not provided - optimize for 3-year data retention
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(90); // Default to last 90 days for better performance

            // Validate date range to prevent excessive data requests
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > 1095) { // More than 3 years
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Date range cannot exceed 3 years (1095 days)".getBytes());
            }
            
            if (daysBetween > 365) { // More than 1 year - warn about performance
                System.out.println("Warning: Large date range requested (" + daysBetween + " days) for analytics PDF");
            }

            // Get historical data
            List<AqiResponse> historicalData = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (historicalData == null || historicalData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No data available for the specified period".getBytes());
            }
            
            // Limit data size for performance - sample data if too large
            if (historicalData.size() > 5000) {
                System.out.println("Sampling large dataset: " + historicalData.size() + " records");
                // Sample every nth record to keep dataset manageable
                int step = historicalData.size() / 5000;
                List<AqiResponse> sampledData = new java.util.ArrayList<>();
                for (int i = 0; i < historicalData.size(); i += step) {
                    sampledData.add(historicalData.get(i));
                }
                historicalData = sampledData;
                System.out.println("Sampled to: " + historicalData.size() + " records");
            }

            // Convert data to JSON for Python script
            String dataJson = objectMapper.writeValueAsString(historicalData);

            // Call Python analytics service
            byte[] pdfData = callPythonAnalyticsService(
                dataJson, 
                city, 
                "pdf", 
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );

            if (pdfData == null || pdfData.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate analytics PDF report".getBytes());
            }

            // Set response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "air-quality-analytics-" + city + "-" +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
            headers.setContentLength(pdfData.length);

            return ResponseEntity.ok().headers(headers).body(pdfData);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generating analytics PDF report: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/analytics-stats")
    public ResponseEntity<?> getAnalyticsStats(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Analytics stats require user authentication");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(7);

            // Get historical data
            List<AqiResponse> historicalData = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (historicalData == null || historicalData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No data available for the specified period");
            }

            // Convert data to JSON for Python script
            String dataJson = objectMapper.writeValueAsString(historicalData);

            // Call Python analytics service for statistics
            String statsJson = callPythonAnalyticsServiceForStats(dataJson, city, "stats");

            if (statsJson == null || statsJson.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate analytics statistics");
            }

            // Parse and return statistics
            Object stats = objectMapper.readValue(statsJson, Object.class);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating analytics statistics: " + e.getMessage());
        }
    }

    @GetMapping("/chart/{chartType}")
    public ResponseEntity<?> generateChart(
            @PathVariable String chartType,
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Chart generation requires user authentication");
            }

            // Validate chart type
            if (!isValidChartType(chartType)) {
                return ResponseEntity.badRequest()
                        .body("Invalid chart type. Supported types: trend_chart, bar_chart, pie_chart, dist_chart");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(7);

            // Get historical data
            List<AqiResponse> historicalData = aqiService.getHistoricalData(city, startDate, endDate);
            
            if (historicalData == null || historicalData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No data available for the specified period");
            }

            // Convert data to JSON for Python script
            String dataJson = objectMapper.writeValueAsString(historicalData);

            // Call Python analytics service for chart generation
            String chartBase64 = callPythonAnalyticsServiceForStats(dataJson, city, chartType);

            if (chartBase64 == null || chartBase64.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate chart");
            }

            // Return base64 image
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(chartBase64.trim());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating chart: " + e.getMessage());
        }
    }

    private boolean isValidChartType(String chartType) {
        return "trend_chart".equals(chartType) || 
               "bar_chart".equals(chartType) || 
               "pie_chart".equals(chartType) || 
               "dist_chart".equals(chartType);
    }

    private byte[] callPythonAnalyticsService(String dataJson, String city, String operation, String startDate, String endDate) {
        try {
            // Create temporary file for data
            File tempDataFile = File.createTempFile("aqi_data_", ".json");
            try (FileWriter writer = new FileWriter(tempDataFile)) {
                writer.write(dataJson);
            }

            // Build command
            ProcessBuilder processBuilder = new ProcessBuilder(
                PYTHON_EXECUTABLE,
                PYTHON_SCRIPT_PATH,
                tempDataFile.getAbsolutePath(),
                city,
                operation,
                startDate,
                endDate
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read output
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (InputStream inputStream = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Wait for process to complete
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python process timed out");
            }

            // Clean up temp file
            tempDataFile.delete();

            if (process.exitValue() != 0) {
                String error = outputStream.toString();
                throw new RuntimeException("Python process failed: " + error);
            }

            // For PDF operation, the output should be the PDF file path or content
            // We need to modify the Python script to return the PDF data directly
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error calling Python analytics service: " + e.getMessage(), e);
        }
    }

    private String callPythonAnalyticsServiceForStats(String dataJson, String city, String operation) {
        try {
            // Create temporary file for data
            File tempDataFile = File.createTempFile("aqi_data_", ".json");
            try (FileWriter writer = new FileWriter(tempDataFile)) {
                writer.write(dataJson);
            }

            // Build command
            ProcessBuilder processBuilder = new ProcessBuilder(
                PYTHON_EXECUTABLE,
                PYTHON_SCRIPT_PATH,
                tempDataFile.getAbsolutePath(),
                city,
                operation
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for process to complete
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python process timed out");
            }

            // Clean up temp file
            tempDataFile.delete();

            if (process.exitValue() != 0) {
                String error = output.toString();
                throw new RuntimeException("Python process failed: " + error);
            }

            return output.toString().trim();

        } catch (Exception e) {
            throw new RuntimeException("Error calling Python analytics service: " + e.getMessage(), e);
        }
    }
}
