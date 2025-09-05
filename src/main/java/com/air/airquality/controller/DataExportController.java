package com.air.airquality.controller;

import com.air.airquality.services.PdfGenerationService;
import com.air.airquality.services.JwtService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class DataExportController {

    private static final Logger logger = LoggerFactory.getLogger(DataExportController.class);


    @Autowired
    private PdfGenerationService pdfGenerationService;

    @Autowired
    private JwtService jwtService;

    /**
     * Generate and download PDF report for air quality data of a specific city
     * PREMIUM FEATURE - Authentication required
     * Generates PDF for ALL available data for the city
     */
    @GetMapping("/pdf/{city}")

    public ResponseEntity<?> exportAirQualityReport(
            @PathVariable String city,
            HttpServletRequest request) {

        try {
            // Validate JWT authentication
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("PDF export request without JWT - denying access (premium feature)");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Authentication required: Bearer token missing"));
            }
            String token = authHeader.substring(7);
            Long userId;
            try {
                userId = jwtService.getUserIdFromToken(token);
            } catch (Exception ex) {
                logger.warn("Invalid JWT for PDF export");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid or expired token"));
            }

            logger.info("Generating PDF report for city: {}, user: {}", city, userId);

            // Generate PDF for all available data for this city
            byte[] pdfData = pdfGenerationService.generateAirQualityReport(city, null, null);

            if (pdfData == null || pdfData.length == 0) {
                logger.error("Empty PDF generated for city: {}", city);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "Failed to generate PDF report"));
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
                    .body(Map.of("success", false, "message", "Error generating PDF report", "error", e.getMessage()));
        }
    }

    /**
     * Alternative endpoint with query parameters instead of path variable
     */
    @GetMapping("/pdf")
    public ResponseEntity<?> exportAirQualityReportByParam(
            @RequestParam String city,
            HttpServletRequest request) {
        
        return exportAirQualityReport(city, request);
    }
}