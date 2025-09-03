package com.air.airquality.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@RestController
@RequestMapping("/api/analytics")
public class PythonAnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(PythonAnalyticsController.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    private static final String PYTHON_SCRIPT_PATH = "python-analytics/database_analytics.py";
    private static final String PYTHON_EXECUTABLE = "python3"; // or "python" on some systems

    @GetMapping("/pdf")
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

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Generating PDF analytics report for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Call Python analytics service
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_EXECUTABLE,
                PYTHON_SCRIPT_PATH,
                city,
                "pdf",
                startDateStr,
                endDateStr
            );
            
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(false);
            
            Process process = pb.start();
            
            // Wait for process to complete with timeout
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                        .body("PDF generation timed out".getBytes());
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
                
                logger.error("Python analytics PDF generation failed with exit code {}: {}", exitCode, errorMsg.toString());
                
                // Handle specific scenarios
                if (errorMsg.toString().contains("not found in database")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(("City '" + city + "' not found in database").getBytes());
                }
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("PDF generation failed: " + errorMsg.toString()).getBytes());
            }
            
            // Read PDF data from stdout
            InputStream inputStream = process.getInputStream();
            byte[] pdfData = inputStream.readAllBytes();
            
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
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Generating line chart for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Call Python analytics service
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_EXECUTABLE,
                PYTHON_SCRIPT_PATH,
                city,
                "line_chart",
                startDateStr,
                endDateStr
            );
            
            pb.directory(new File(System.getProperty("user.dir")));
            Process process = pb.start();
            
            // Wait for process to complete
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Chart generation timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorMsg.append(line).append("\n");
                }
                
                logger.error("Python line chart generation failed: {}", errorMsg.toString());
                
                if (errorMsg.toString().contains("not found in database")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("City '" + city + "' not found in database");
                }
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Chart generation failed: " + errorMsg.toString());
            }
            
            // Read chart data (base64) from stdout
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder chartData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                chartData.append(line);
            }
            
            logger.info("Successfully generated line chart for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(chartData.toString());
            
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
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Generating histogram for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Call Python analytics service
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_EXECUTABLE,
                PYTHON_SCRIPT_PATH,
                city,
                "histogram",
                startDateStr,
                endDateStr
            );
            
            pb.directory(new File(System.getProperty("user.dir")));
            Process process = pb.start();
            
            // Wait for process to complete
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Chart generation timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorMsg.append(line).append("\n");
                }
                
                logger.error("Python histogram generation failed: {}", errorMsg.toString());
                
                if (errorMsg.toString().contains("not found in database")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("City '" + city + "' not found in database");
                }
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Chart generation failed: " + errorMsg.toString());
            }
            
            // Read chart data (base64) from stdout
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder chartData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                chartData.append(line);
            }
            
            logger.info("Successfully generated histogram for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(chartData.toString());
            
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
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
            }

            // Set default dates if not provided
            if (endDate == null) endDate = LocalDateTime.now();
            if (startDate == null) startDate = endDate.minusDays(30);

            logger.info("Getting statistics for city: {}, from: {}, to: {}", city, startDate, endDate);

            // Format dates for Python script
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startDateStr = startDate.format(formatter);
            String endDateStr = endDate.format(formatter);

            // Call Python analytics service
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_EXECUTABLE,
                PYTHON_SCRIPT_PATH,
                city,
                "stats",
                startDateStr,
                endDateStr
            );
            
            pb.directory(new File(System.getProperty("user.dir")));
            Process process = pb.start();
            
            // Wait for process to complete
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Statistics generation timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorMsg.append(line).append("\n");
                }
                
                logger.error("Python stats generation failed: {}", errorMsg.toString());
                
                if (errorMsg.toString().contains("not found in database")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("City '" + city + "' not found in database");
                }
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Statistics generation failed: " + errorMsg.toString());
            }
            
            // Read stats data (JSON) from stdout
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder statsData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                statsData.append(line);
            }
            
            logger.info("Successfully generated statistics for city: {}", city);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(statsData.toString());
            
        } catch (Exception e) {
            logger.error("Error generating statistics for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating statistics: " + e.getMessage());
        }
    }
}