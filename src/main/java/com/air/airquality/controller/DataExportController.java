package com.air.airquality.controller;

import com.air.airquality.services.PdfGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/export")
public class DataExportController {

    private static final Logger logger = LoggerFactory.getLogger(DataExportController.class);

    @Autowired
    private PdfGenerationService pdfGenerationService;

    /**
     * Generate and download PDF report for air quality data of a specific city
     * PREMIUM FEATURE - Authentication required
     */
    @GetMapping("/pdf/{city}")
    public ResponseEntity<byte[]> exportAirQualityReport(
            @PathVariable String city,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {

        try {
            // Validate authentication - PDF export is a premium feature
            String userId = request.getHeader("X-User-Id");
            if (userId == null || userId.isEmpty()) {
                logger.warn("PDF export request without authentication - denying access (premium feature)");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication required to access premium features like PDF export".getBytes());
            }

            logger.info("Generating PDF report for city: {}, user: {}", city, userId);

            // Parse dates with defaults
            LocalDateTime endDateTime = null;
            LocalDateTime startDateTime = null;
            
            if (endDate != null && !endDate.isEmpty()) {
                try {
                    endDateTime = LocalDateTime.parse(endDate);
                } catch (Exception e) {
                    logger.warn("Invalid end date format: {}, using default", endDate);
                }
            }
            
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    startDateTime = LocalDateTime.parse(startDate);
                } catch (Exception e) {
                    logger.warn("Invalid start date format: {}, using default", startDate);
                }
            }
            
            // Set defaults if not provided or parsing failed
            if (endDateTime == null) {
                endDateTime = LocalDateTime.now();
            }
            if (startDateTime == null) {
                startDateTime = endDateTime.minusDays(30); // Default to last 30 days
            }

            // Generate PDF
            byte[] pdfData = pdfGenerationService.generateAirQualityReport(city, startDateTime, endDateTime);

            if (pdfData == null || pdfData.length == 0) {
                logger.error("Empty PDF generated for city: {}", city);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate PDF report".getBytes());
            }

            // Prepare response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    String.format("air_quality_report_%s_%s.pdf",
                            city.replaceAll("[^a-zA-Z0-9]", "_"),
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))));
            headers.setContentLength(pdfData.length);

            logger.info("Successfully generated PDF report for city: {} ({} bytes)", city, pdfData.length);
            return new ResponseEntity<>(pdfData, headers, HttpStatus.OK);

        } catch (Exception e) {
            logger.error("Error generating PDF report for city {}: {}", city, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generating PDF report: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Alternative endpoint with query parameters instead of path variable
     */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportAirQualityReportByParam(
            @RequestParam String city,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        
        return exportAirQualityReport(city, startDate, endDate, request);
    }
}