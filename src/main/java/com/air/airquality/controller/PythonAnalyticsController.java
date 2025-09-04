package com.air.airquality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/analytics")
public class PythonAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(PythonAnalyticsController.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String PYTHON_SCRIPT_PATH = "python-analytics/database_analytics.py";
    private static final String PYTHON_SERVICE_MANAGER = "python-analytics/service_manager.py";
    private static final String PYTHON_EXECUTABLE = "python"; // or "python3" on some systems
    
    // Cache for Python setup status
    private static Boolean pythonSetupComplete = null;
    private static final Object setupLock = new Object();

    /**
     * Test endpoint to verify authentication and basic functionality
     */
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint(HttpServletRequest request) {
        try {
            String userId = request.getHeader("X-User-Id");
            String authorization = request.getHeader("Authorization");
            
            logger.info("Test endpoint called - UserId: {}, Authorization present: {}", 
                       userId, authorization != null ? "Yes" : "No");
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("message", "Analytics endpoint is working");
            response.put("userId", userId != null ? userId : "Not provided");
            response.put("authProvided", authorization != null);
            response.put("pythonSetupStatus", pythonSetupComplete != null ? pythonSetupComplete.toString() : "not checked");
            response.put("timestamp", LocalDateTime.now().toString());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.toString());
                    
        } catch (Exception e) {
            logger.error("Error in test endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"message\": \"Test failed: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Ensure Python environment is set up with all dependencies
     */
    private boolean ensurePythonSetup() {
        synchronized (setupLock) {
            if (pythonSetupComplete != null) {
                return pythonSetupComplete;
            }
            
            logger.info("Setting up Python analytics environment...");
            
            try {
                String[] pythonCommands = {"python", "python3", "py"};
                String workingDir = System.getProperty("user.dir");
                File serviceManagerFile = new File(workingDir, PYTHON_SERVICE_MANAGER);
                
                if (!serviceManagerFile.exists()) {
                    logger.error("Python service manager not found at: {}", serviceManagerFile.getAbsolutePath());
                    pythonSetupComplete = false;
                    return false;
                }
                
                // Try different Python commands until one works
                for (String pythonCmd : pythonCommands) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(pythonCmd, PYTHON_SERVICE_MANAGER, "setup");
                        pb.directory(new File(workingDir));
                        pb.redirectErrorStream(false);
                        
                        Process process = pb.start();
                        boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5 minutes for setup
                        
                        if (!finished) {
                            process.destroyForcibly();
                            continue;
                        }
                        
                        int exitCode = process.exitValue();
                        if (exitCode == 0) {
                            logger.info("Python analytics environment set up successfully with {}", pythonCmd);
                            pythonSetupComplete = true;
                            return true;
                        } else {
                            // Read error output for debugging
                            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                            StringBuilder errorMsg = new StringBuilder();
                            String line;
                            while ((line = errorReader.readLine()) != null) {
                                errorMsg.append(line).append("\n");
                            }
                            logger.warn("Python setup failed with {}: {}", pythonCmd, errorMsg.toString());
                        }
                        
                    } catch (Exception e) {
                        logger.debug("Failed to setup with {}: {}", pythonCmd, e.getMessage());
                        continue;
                    }
                }
                
                logger.error("Failed to set up Python analytics environment with any available Python command");
                pythonSetupComplete = false;
                return false;
                
            } catch (Exception e) {
                logger.error("Error during Python setup: {}", e.getMessage(), e);
                pythonSetupComplete = false;
                return false;
            }
        }
    }
    
    /**
     * Main endpoint for generating comprehensive analytics with charts and statistics.
     * This endpoint automatically starts Python and handles all 3 criteria:
     * 1. Statistics generation
     * 2. Chart generation (line chart and histogram)
     * 3. Data availability handling
     */
    @GetMapping("/generate/{city}")
    public ResponseEntity<String> generateAnalytics(
            @PathVariable String city,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            String authorization = request.getHeader("Authorization");
            
            logger.info("Analytics request - City: {}, UserId: {}, Auth: {}", 
                       city, userId, authorization != null ? "Present" : "Missing");
            
            // Analytics are available for all users - authentication is optional but logged
            if (userId == null || userId.isEmpty()) {
                logger.info("Analytics request without user ID - proceeding as guest user");
            }
            
            if (authorization == null || authorization.isEmpty()) {
                logger.info("Analytics request without authorization - proceeding with limited access");
            }

            logger.info("Generating comprehensive analytics for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Ensure Python environment is set up
            if (!ensurePythonSetup()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"success\": false, \"message\": \"Python analytics environment not available. Please ensure Python and required packages are installed.\"}");
            }

            // Set default dates if not provided with better error handling
            LocalDateTime endDateTime;
            LocalDateTime startDateTime;
            
            try {
                endDateTime = endDate != null ? LocalDateTime.parse(endDate) : LocalDateTime.now();
                startDateTime = startDate != null ? LocalDateTime.parse(startDate) : endDateTime.minusDays(7);
            } catch (Exception dateParseException) {
                logger.warn("Date parsing failed for startDate: '{}', endDate: '{}'. Using defaults.", startDate, endDate);
                endDateTime = LocalDateTime.now();
                startDateTime = endDateTime.minusDays(7);
            }

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDateTime.format(formatter);
            String endDateStr = endDateTime.format(formatter);

            // Run all analytics operations concurrently for better performance
            CompletableFuture<String> statsTask = generateStatsAsync(city, startDateStr, endDateStr);
            CompletableFuture<String> lineChartTask = generateLineChartAsync(city, startDateStr, endDateStr);
            CompletableFuture<String> histogramTask = generateHistogramAsync(city, startDateStr, endDateStr);

            // Wait for all tasks to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(statsTask, lineChartTask, histogramTask);
            allTasks.get(180, TimeUnit.SECONDS); // 3 minutes timeout

            // Get results
            String statsJson = statsTask.get();
            String lineChartB64 = lineChartTask.get();
            String histogramB64 = histogramTask.get();

            // Parse statistics JSON to check for errors
            ObjectNode statsNode;
            try {
                statsNode = (ObjectNode) objectMapper.readTree(statsJson);
            } catch (Exception parseException) {
                logger.error("Failed to parse statistics JSON: {}", statsJson, parseException);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"success\": false, \"message\": \"Failed to parse analytics results\"}");
            }
            
            // Create comprehensive response
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("city", city);
            response.put("startDate", startDateStr);
            response.put("endDate", endDateStr);
            response.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Add statistics
            response.set("statistics", statsNode);
            
            // Add charts
            ObjectNode charts = objectMapper.createObjectNode();
            charts.put("trendChart", lineChartB64);
            charts.put("barChart", lineChartB64); // Using line chart as trend for now
            charts.put("pieChart", histogramB64); // Using histogram as distribution
            charts.put("distributionChart", histogramB64);
            response.set("charts", charts);
            
            // Add data availability info with null checks
            ObjectNode dataInfo = objectMapper.createObjectNode();
            dataInfo.put("dataAvailable", true);
            if (statsNode.has("scenario")) {
                dataInfo.put("scenario", statsNode.get("scenario").asInt());
            }
            if (statsNode.has("total_records")) {
                dataInfo.put("recordCount", statsNode.get("total_records").asInt());
            }
            response.set("dataAvailability", dataInfo);

            logger.info("Successfully generated comprehensive analytics for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.toString());

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getMessage().contains("not found in database")) {
                logger.warn("City '{}' not found in database", city);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(createNoDataResponse(city, "City not found in database"));
            }
            
            logger.error("Error generating analytics for city {}: {}", city, cause.getMessage(), cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"message\": \"Error generating analytics: " + cause.getMessage() + "\"}");
            
        } catch (Exception e) {
            logger.error("Error generating analytics for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"message\": \"Error generating analytics: " + e.getMessage() + "\"}");
        }
    }

    /**
     * PDF generation endpoint that automatically calls Python analytics
     */
    @GetMapping("/pdf/{city}")
    public ResponseEntity<byte[]> exportAnalyticsPDFByCity(
            @PathVariable String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        return exportAnalyticsPDF(city, startDate, endDate, request);
    }

    /**
     * Asynchronously generate statistics using Python
     */
    private CompletableFuture<String> generateStatsAsync(String city, String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executePythonScript(city, "stats", startDate, endDate, false);
            } catch (Exception e) {
                throw new RuntimeException("Stats generation failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Asynchronously generate line chart using Python
     */
    private CompletableFuture<String> generateLineChartAsync(String city, String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executePythonScript(city, "line_chart", startDate, endDate, false);
            } catch (Exception e) {
                throw new RuntimeException("Line chart generation failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Asynchronously generate histogram using Python
     */
    private CompletableFuture<String> generateHistogramAsync(String city, String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executePythonScript(city, "histogram", startDate, endDate, false);
            } catch (Exception e) {
                throw new RuntimeException("Histogram generation failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Execute Python script with proper error handling and automatic startup
     */
    private String executePythonScript(String city, String operation, String startDate, String endDate, boolean binaryOutput) 
            throws IOException, InterruptedException {
        
        // Ensure Python is set up before executing
        if (!ensurePythonSetup()) {
            throw new IOException("Python analytics environment not available");
        }
        
        // Try different Python executables
        String[] pythonCommands = {"python", "python3", "py"};
        String workingDir = System.getProperty("user.dir");
        File serviceManagerFile = new File(workingDir, PYTHON_SERVICE_MANAGER);
        
        if (!serviceManagerFile.exists()) {
            throw new IOException("Python service manager not found at: " + serviceManagerFile.getAbsolutePath());
        }
        
        ProcessBuilder pb = null;
        Process process = null;
        IOException lastException = null;
        
        // Try different Python commands until one works
        for (String pythonCmd : pythonCommands) {
            try {
                // Use service manager to run analytics
                pb = new ProcessBuilder(
                    pythonCmd,
                    PYTHON_SERVICE_MANAGER,
                    "run",
                    city,
                    operation,
                    startDate,
                    endDate
                );
                
                pb.directory(new File(workingDir));
                pb.redirectErrorStream(false);
                
                logger.debug("Executing Python command via service manager: {} {} run {} {} {} {}", 
                           pythonCmd, PYTHON_SERVICE_MANAGER, city, operation, startDate, endDate);
                
                process = pb.start();
                
                // Wait for process to complete with timeout
                int timeoutSeconds = operation.equals("pdf") ? 180 : 90;
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                
                if (!finished) {
                    process.destroyForcibly();
                    throw new RuntimeException("Python script execution timed out after " + timeoutSeconds + " seconds");
                }
                
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    // Read error output
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    StringBuilder errorMsg = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorMsg.append(line).append("\n");
                    }
                    
                    String errorMessage = errorMsg.toString();
                    logger.error("Python script failed with exit code {}: {}", exitCode, errorMessage);
                    
                    // Handle specific error cases
                    if (errorMessage.contains("not found in database")) {
                        throw new RuntimeException("City '" + city + "' not found in database");
                    } else if (errorMessage.contains("No module named") || errorMessage.contains("ModuleNotFoundError")) {
                        // Reset setup cache and try next Python command
                        pythonSetupComplete = null;
                        lastException = new IOException("Python dependencies missing: " + errorMessage);
                        continue;
                    } else {
                        throw new RuntimeException("Python script execution failed: " + errorMessage);
                    }
                }
                
                // Successfully executed, read output
                if (binaryOutput) {
                    // For PDF output, return as binary
                    InputStream inputStream = process.getInputStream();
                    byte[] data = inputStream.readAllBytes();
                    return new String(data, "ISO-8859-1"); // Binary encoding
                } else {
                    // For text output (stats, charts)
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                        if (operation.equals("stats")) {
                            output.append("\n"); // Preserve JSON formatting
                        }
                    }
                    return output.toString();
                }
                
            } catch (IOException e) {
                lastException = e;
                logger.debug("Failed to execute with {}: {}", pythonCmd, e.getMessage());
                // Try next Python command
                continue;
            }
        }
        
        // If we get here, all Python commands failed
        if (lastException != null) {
            throw new IOException("Failed to execute Python script with any available Python command. Last error: " + lastException.getMessage(), lastException);
        } else {
            throw new IOException("No suitable Python interpreter found. Please ensure Python is installed and available in PATH.");
        }
    }

    /**
     * Create a standardized "no data" response
     */
    private String createNoDataResponse(String city, String reason) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", false);
            response.put("dataAvailable", false);
            response.put("suggestDataGeneration", true);
            response.put("city", city);
            response.put("message", reason);
            response.put("recommendation", "Consider generating sample data or checking city name spelling");
            
            return response.toString();
        } catch (Exception e) {
            return "{\"success\": false, \"message\": \"" + reason + "\"}";
        }
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportAnalyticsPDF(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                logger.info("PDF export request without user ID - proceeding as guest");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Generating PDF analytics report for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Use the new executePythonScript method for better error handling
            String pdfDataStr = executePythonScript(city, "pdf", startDateStr, endDateStr, true);
            byte[] pdfData = pdfDataStr.getBytes("ISO-8859-1"); // Convert back from binary encoding
            
            if (pdfData.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Empty PDF generated".getBytes());
            }
            
            // Return PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                String.format("air_quality_report_%s_%s.pdf", 
                    city.replaceAll(" ", "_"), 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))));
            headers.setContentLength(pdfData.length);
            
            logger.info("Successfully generated PDF report for city: {} ({} bytes)", city, pdfData.length);
            return new ResponseEntity<>(pdfData, headers, HttpStatus.OK);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found in database")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(("City '" + city + "' not found in database").getBytes());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("PDF generation failed: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            logger.error("Error generating analytics PDF for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generating PDF: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/charts/line")
    public ResponseEntity<String> getLineChart(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                logger.info("Line chart request without user ID - proceeding as guest");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Generating line chart for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Use the new executePythonScript method
            String chartData = executePythonScript(city, "line_chart", startDateStr, endDateStr, false);
            
            logger.info("Successfully generated line chart for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(chartData);
                    
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found in database")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("City '" + city + "' not found in database");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Chart generation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error generating line chart for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating chart: " + e.getMessage());
        }
    }

    @GetMapping("/charts/histogram")
    public ResponseEntity<String> getHistogram(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                logger.info("Histogram request without user ID - proceeding as guest");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Generating histogram for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Use the new executePythonScript method
            String chartData = executePythonScript(city, "histogram", startDateStr, endDateStr, false);
            
            logger.info("Successfully generated histogram for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(chartData);
                    
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found in database")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("City '" + city + "' not found in database");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Chart generation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error generating histogram for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating chart: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<String> getStats(
            @RequestParam String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletRequest request) {
        
        try {
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                logger.info("Stats request without user ID - proceeding as guest");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Getting statistics for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Use the new executePythonScript method
            String statsData = executePythonScript(city, "stats", startDateStr, endDateStr, false);
            
            logger.info("Successfully generated statistics for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(statsData);
                    
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found in database")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("City '" + city + "' not found in database");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Statistics generation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error generating statistics for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating statistics: " + e.getMessage());
        }
    }
}