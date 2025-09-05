package com.air.airquality.services;

import com.air.airquality.model.AqiData;
import com.air.airquality.repository.AqiDataRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

@Service
public class PdfGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationService.class);

    @Autowired
    private AqiDataRepository aqiDataRepository;

    public byte[] generateAirQualityReport(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Fetch data from database
            java.util.List<AqiData> aqiDataList;
            
            if (startDate == null || endDate == null) {
                // If no date range provided, get all data for the city
                logger.info("Generating PDF report for city: {} for all available data", city);
                
                // Find the actual date range for this city
                Optional<LocalDateTime> oldestDate = aqiDataRepository.findOldestDateByCity(city);
                Optional<LocalDateTime> newestDate = aqiDataRepository.findNewestDateByCity(city);
                
                if (oldestDate.isPresent() && newestDate.isPresent()) {
                    startDate = oldestDate.get();
                    endDate = newestDate.get();
                    aqiDataList = aqiDataRepository.findByCityAndTimestampBetween(city, startDate, endDate);
                } else {
                    // No data for this city
                    startDate = LocalDateTime.now().minusDays(30);
                    endDate = LocalDateTime.now();
                    aqiDataList = new java.util.ArrayList<>();
                }
            } else {
                logger.info("Generating PDF report for city: {} from {} to {}", city, startDate, endDate);
                aqiDataList = aqiDataRepository.findByCityAndTimestampBetween(city, startDate, endDate);
            }

            if (aqiDataList.isEmpty()) {
                logger.warn("No data found for city: {} in the specified date range", city);
                return generateNoDataReport(city, startDate, endDate);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Add title and header
            addReportHeader(document, city, startDate, endDate, aqiDataList.size());

            // Add executive summary
            addExecutiveSummary(document, aqiDataList);

            // Add detailed statistics
            addDetailedStatistics(document, aqiDataList);

            // Add data table
            addDataTable(document, aqiDataList);

            // Add health recommendations
            addHealthRecommendations(document, aqiDataList);

            // Add footer
            addReportFooter(document);

            document.close();
            logger.info("PDF report generated successfully for city: {} ({} bytes)", city, outputStream.size());

            return outputStream.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating PDF report for city {}: {}", city, e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    private void addReportHeader(Document document, String city, LocalDateTime startDate, 
                                LocalDateTime endDate, int recordCount) {
        
        // Title
        Paragraph title = new Paragraph("Air Quality Report")
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10);
        document.add(title);

        // Subtitle
        Paragraph subtitle = new Paragraph(String.format("City: %s", city))
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(subtitle);

        // Report info table
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        infoTable.addCell(createInfoCell("Report Period:"));
        infoTable.addCell(createInfoCell(String.format("%s to %s", 
                startDate.format(formatter), endDate.format(formatter))));
        
        infoTable.addCell(createInfoCell("Total Records:"));
        infoTable.addCell(createInfoCell(String.valueOf(recordCount)));
        
        infoTable.addCell(createInfoCell("Generated On:"));
        infoTable.addCell(createInfoCell(LocalDateTime.now().format(formatter)));

        document.add(infoTable);
    }

    private Cell createInfoCell(String text) {
        return new Cell().add(new Paragraph(text))
                .setBorder(Border.NO_BORDER)
                .setPadding(5);
    }

    private void addExecutiveSummary(Document document, java.util.List<AqiData> aqiDataList) {
        document.add(new Paragraph("Executive Summary")
                .setFontSize(16)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10));

        // Calculate basic statistics
        OptionalDouble avgAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).average();
        OptionalInt maxAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).max();
        OptionalInt minAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).min();

        Map<String, Integer> aqiLevelCounts = getAqiLevelCounts(aqiDataList);

        Paragraph summary = new Paragraph()
                .add(String.format("This report analyzes %d air quality measurements. ", aqiDataList.size()))
                .add(String.format("The average AQI during this period was %.1f, ", avgAqi.orElse(0.0)))
                .add(String.format("with values ranging from %d to %d. ", minAqi.orElse(0), maxAqi.orElse(0)))
                .add(getDominantAqiLevelText(aqiLevelCounts))
                .setMarginBottom(15);

        document.add(summary);
    }

    private void addDetailedStatistics(Document document, java.util.List<AqiData> aqiDataList) {
        document.add(new Paragraph("Detailed Statistics")
                .setFontSize(16)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10));

        // Statistics table
        Table statsTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        // Header
        statsTable.addHeaderCell(createStatsHeaderCell("Pollutant"));
        statsTable.addHeaderCell(createStatsHeaderCell("Average"));
        statsTable.addHeaderCell(createStatsHeaderCell("Minimum"));
        statsTable.addHeaderCell(createStatsHeaderCell("Maximum"));

        // AQI
        OptionalDouble avgAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).average();
        OptionalInt minAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).min();
        OptionalInt maxAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).max();
        addStatsRow(statsTable, "AQI", 
                String.format("%.1f", avgAqi.orElse(0.0)),
                String.valueOf(minAqi.orElse(0)),
                String.valueOf(maxAqi.orElse(0)));

        // PM2.5
        java.util.List<AqiData> pm25Data = aqiDataList.stream().filter(d -> d.getPm25() != null).collect(Collectors.toList());
        addPollutantStatsFromList(statsTable, "PM2.5 (μg/m³)", pm25Data, AqiData::getPm25);

        // PM10
        java.util.List<AqiData> pm10Data = aqiDataList.stream().filter(d -> d.getPm10() != null).collect(Collectors.toList());
        addPollutantStatsFromList(statsTable, "PM10 (μg/m³)", pm10Data, AqiData::getPm10);

        // NO2
        java.util.List<AqiData> no2Data = aqiDataList.stream().filter(d -> d.getNo2() != null).collect(Collectors.toList());
        addPollutantStatsFromList(statsTable, "NO2 (ppb)", no2Data, AqiData::getNo2);

        // SO2
        java.util.List<AqiData> so2Data = aqiDataList.stream().filter(d -> d.getSo2() != null).collect(Collectors.toList());
        addPollutantStatsFromList(statsTable, "SO2 (ppb)", so2Data, AqiData::getSo2);

        // CO
        java.util.List<AqiData> coData = aqiDataList.stream().filter(d -> d.getCo() != null).collect(Collectors.toList());
        addPollutantStatsFromList(statsTable, "CO (ppm)", coData, AqiData::getCo);

        // O3
        java.util.List<AqiData> o3Data = aqiDataList.stream().filter(d -> d.getO3() != null).collect(Collectors.toList());
        addPollutantStatsFromList(statsTable, "O3 (ppb)", o3Data, AqiData::getO3);

        document.add(statsTable);

        // AQI Level Distribution
        addAqiLevelDistribution(document, aqiDataList);
    }

    private void addPollutantStatsFromList(Table table, String pollutant, 
                                           java.util.List<AqiData> dataList, 
                                           java.util.function.Function<AqiData, Double> valueExtractor) {
        OptionalDouble avg = dataList.stream().mapToDouble(d -> valueExtractor.apply(d)).average();
        OptionalDouble min = dataList.stream().mapToDouble(d -> valueExtractor.apply(d)).min();
        OptionalDouble max = dataList.stream().mapToDouble(d -> valueExtractor.apply(d)).max();

        addStatsRow(table, pollutant,
                avg.isPresent() ? String.format("%.2f", avg.getAsDouble()) : "N/A",
                min.isPresent() ? String.format("%.2f", min.getAsDouble()) : "N/A",
                max.isPresent() ? String.format("%.2f", max.getAsDouble()) : "N/A");
    }

    private Cell createStatsHeaderCell(String text) {
        return new Cell().add(new Paragraph(text).setBold())
                .setBackgroundColor(new DeviceRgb(230, 230, 230))
                .setPadding(8)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private void addStatsRow(Table table, String pollutant, String avg, String min, String max) {
        table.addCell(new Cell().add(new Paragraph(pollutant)).setPadding(5));
        table.addCell(new Cell().add(new Paragraph(avg)).setPadding(5).setTextAlignment(TextAlignment.CENTER));
        table.addCell(new Cell().add(new Paragraph(min)).setPadding(5).setTextAlignment(TextAlignment.CENTER));
        table.addCell(new Cell().add(new Paragraph(max)).setPadding(5).setTextAlignment(TextAlignment.CENTER));
    }

    private void addAqiLevelDistribution(Document document, java.util.List<AqiData> aqiDataList) {
        Map<String, Integer> levelCounts = getAqiLevelCounts(aqiDataList);
        
        document.add(new Paragraph("AQI Level Distribution")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10));

        Table levelTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(60))
                .setMarginBottom(20);

        levelTable.addHeaderCell(createStatsHeaderCell("AQI Level"));
        levelTable.addHeaderCell(createStatsHeaderCell("Count"));
        levelTable.addHeaderCell(createStatsHeaderCell("Percentage"));

        int total = aqiDataList.size();
        for (Map.Entry<String, Integer> entry : levelCounts.entrySet()) {
            String level = entry.getKey();
            int count = entry.getValue();
            double percentage = (count * 100.0) / total;
            
            Cell levelCell = new Cell().add(new Paragraph(level)).setPadding(5);
            levelCell.setBackgroundColor(getAqiLevelColor(level));
            
            levelTable.addCell(levelCell);
            levelTable.addCell(new Cell().add(new Paragraph(String.valueOf(count)))
                    .setPadding(5).setTextAlignment(TextAlignment.CENTER));
            levelTable.addCell(new Cell().add(new Paragraph(String.format("%.1f%%", percentage)))
                    .setPadding(5).setTextAlignment(TextAlignment.CENTER));
        }

        document.add(levelTable);
    }

    private void addDataTable(Document document, java.util.List<AqiData> aqiDataList) {
        document.add(new Paragraph("Recent Air Quality Data (Last 20 records)")
                .setFontSize(16)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10));

        Table dataTable = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1, 1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        // Headers
        dataTable.addHeaderCell(createStatsHeaderCell("Timestamp"));
        dataTable.addHeaderCell(createStatsHeaderCell("AQI"));
        dataTable.addHeaderCell(createStatsHeaderCell("PM2.5"));
        dataTable.addHeaderCell(createStatsHeaderCell("PM10"));
        dataTable.addHeaderCell(createStatsHeaderCell("NO2"));
        dataTable.addHeaderCell(createStatsHeaderCell("SO2"));
        dataTable.addHeaderCell(createStatsHeaderCell("CO"));
        dataTable.addHeaderCell(createStatsHeaderCell("O3"));

        // Data rows (limit to most recent 20)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        java.util.List<AqiData> recentData = aqiDataList.stream().limit(20).collect(Collectors.toList());
        
        for (AqiData data : recentData) {
            dataTable.addCell(new Cell().add(new Paragraph(data.getTimestamp().format(formatter))).setPadding(3));
            
            Cell aqiCell = new Cell().add(new Paragraph(String.valueOf(data.getAqiValue())))
                    .setPadding(3).setTextAlignment(TextAlignment.CENTER);
            aqiCell.setBackgroundColor(getAqiValueColor(data.getAqiValue()));
            dataTable.addCell(aqiCell);
            
            dataTable.addCell(createDataCell(data.getPm25()));
            dataTable.addCell(createDataCell(data.getPm10()));
            dataTable.addCell(createDataCell(data.getNo2()));
            dataTable.addCell(createDataCell(data.getSo2()));
            dataTable.addCell(createDataCell(data.getCo()));
            dataTable.addCell(createDataCell(data.getO3()));
        }

        document.add(dataTable);
    }

    private Cell createDataCell(Double value) {
        String text = value != null ? String.format("%.1f", value) : "-";
        return new Cell().add(new Paragraph(text))
                .setPadding(3)
                .setTextAlignment(TextAlignment.CENTER);
    }

    private void addHealthRecommendations(Document document, java.util.List<AqiData> aqiDataList) {
        document.add(new Paragraph("Health Recommendations & Precautions")
                .setFontSize(16)
                .setBold()
                .setMarginTop(20)
                .setMarginBottom(10));

        // Determine current air quality level
        OptionalDouble avgAqi = aqiDataList.stream().mapToInt(AqiData::getAqiValue).average();
        String currentLevel = getAqiLevel((int) avgAqi.orElse(0));

        // Add current status
        Paragraph status = new Paragraph()
                .add("Current Air Quality Status: ")
                .add(new Text(currentLevel).setBold())
                .setMarginBottom(10);
        document.add(status);

        // Add recommendations based on current level
        java.util.List<String> recommendations = getHealthRecommendations(currentLevel);
        com.itextpdf.layout.element.List list = new com.itextpdf.layout.element.List()
                .setSymbolIndent(12)
                .setListSymbol("\u2022")
                .setMarginBottom(15);

        for (String recommendation : recommendations) {
            list.add(new ListItem(recommendation));
        }
        document.add(list);

        // Add general health guidelines
        addGeneralHealthGuidelines(document);

        // Add emergency contacts
        addEmergencyContacts(document);
    }

    private void addGeneralHealthGuidelines(Document document) {
        document.add(new Paragraph("General Health Guidelines")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10));

        java.util.List<String> guidelines = Arrays.asList(
                "Monitor air quality regularly, especially if you have respiratory conditions",
                "Keep windows closed during high pollution periods",
                "Use air purifiers indoors when possible",
                "Avoid outdoor exercise during poor air quality periods",
                "Consider wearing N95 masks when air quality is unhealthy",
                "Stay hydrated and maintain a healthy diet rich in antioxidants",
                "Consult healthcare providers if you experience breathing difficulties"
        );

        com.itextpdf.layout.element.List guidelinesList = new com.itextpdf.layout.element.List()
                .setSymbolIndent(12)
                .setListSymbol("\u2022")
                .setMarginBottom(15);

        for (String guideline : guidelines) {
            guidelinesList.add(new ListItem(guideline));
        }
        document.add(guidelinesList);
    }

    private void addEmergencyContacts(Document document) {
        document.add(new Paragraph("Emergency Information")
                .setFontSize(14)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10));

        Paragraph emergency = new Paragraph()
                .add("If you experience severe breathing difficulties, chest pain, or other serious symptoms related to air pollution exposure, seek immediate medical attention or call emergency services.\n\n")
                .add("For air quality alerts and updates, visit your local environmental agency website or use air quality monitoring apps.")
                .setMarginBottom(20);
        
        document.add(emergency);
    }

    private void addReportFooter(Document document) {
        Paragraph footer = new Paragraph()
                .add("This report is generated based on available air quality monitoring data. ")
                .add("Air quality conditions can change rapidly. For real-time updates, please check current monitoring data.")
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(30);
        
        document.add(footer);
    }

    private Map<String, Integer> getAqiLevelCounts(java.util.List<AqiData> aqiDataList) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Good", 0);
        counts.put("Moderate", 0);
        counts.put("Unhealthy for Sensitive Groups", 0);
        counts.put("Unhealthy", 0);
        counts.put("Very Unhealthy", 0);
        counts.put("Hazardous", 0);

        for (AqiData data : aqiDataList) {
            String level = getAqiLevel(data.getAqiValue());
            counts.put(level, counts.get(level) + 1);
        }

        return counts;
    }

    private String getAqiLevel(int aqi) {
        if (aqi <= 50) return "Good";
        else if (aqi <= 100) return "Moderate";
        else if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        else if (aqi <= 200) return "Unhealthy";
        else if (aqi <= 300) return "Very Unhealthy";
        else return "Hazardous";
    }

    private DeviceRgb getAqiLevelColor(String level) {
        switch (level) {
            case "Good": return new DeviceRgb(0, 228, 0);
            case "Moderate": return new DeviceRgb(255, 255, 0);
            case "Unhealthy for Sensitive Groups": return new DeviceRgb(255, 126, 0);
            case "Unhealthy": return new DeviceRgb(255, 0, 0);
            case "Very Unhealthy": return new DeviceRgb(143, 63, 151);
            case "Hazardous": return new DeviceRgb(126, 0, 35);
            default: return new DeviceRgb(255, 255, 255);
        }
    }

    private DeviceRgb getAqiValueColor(int aqi) {
        if (aqi <= 50) return new DeviceRgb(0, 228, 0);
        else if (aqi <= 100) return new DeviceRgb(255, 255, 0);
        else if (aqi <= 150) return new DeviceRgb(255, 126, 0);
        else if (aqi <= 200) return new DeviceRgb(255, 0, 0);
        else if (aqi <= 300) return new DeviceRgb(143, 63, 151);
        else return new DeviceRgb(126, 0, 35);
    }

    private String getDominantAqiLevelText(Map<String, Integer> levelCounts) {
        String dominantLevel = levelCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
        
        return String.format("The air quality was predominantly %s during this period.", dominantLevel);
    }

    private java.util.List<String> getHealthRecommendations(String aqiLevel) {
        switch (aqiLevel) {
            case "Good":
                return Arrays.asList(
                        "Air quality is excellent - ideal for all outdoor activities",
                        "Perfect time for exercise, sports, and outdoor recreation",
                        "No health precautions necessary for any group"
                );
            
            case "Moderate":
                return Arrays.asList(
                        "Air quality is acceptable for most people",
                        "Unusually sensitive individuals may experience minor symptoms",
                        "Outdoor activities are generally safe for healthy individuals",
                        "Consider reducing prolonged outdoor exertion if sensitive to air pollution"
                );
            
            case "Unhealthy for Sensitive Groups":
                return Arrays.asList(
                        "Sensitive groups (children, elderly, people with heart/lung disease) should limit outdoor activities",
                        "Healthy individuals can continue normal outdoor activities",
                        "Consider indoor exercise alternatives if you're in a sensitive group",
                        "Watch for symptoms like coughing, throat irritation, or breathing difficulty"
                );
            
            case "Unhealthy":
                return Arrays.asList(
                        "Everyone should limit prolonged outdoor exertion",
                        "Sensitive groups should avoid outdoor activities entirely",
                        "Consider wearing N95 masks when outdoors",
                        "Keep windows closed and use air purifiers if available",
                        "Reschedule outdoor events if possible"
                );
            
            case "Very Unhealthy":
                return Arrays.asList(
                        "Avoid all outdoor activities",
                        "Everyone should stay indoors with windows and doors closed",
                        "Use air purifiers and avoid using anything that creates more particles indoors",
                        "Wear N95 masks if you must go outside",
                        "Seek medical attention if experiencing breathing difficulties"
                );
            
            case "Hazardous":
                return Arrays.asList(
                        "EMERGENCY CONDITIONS: Everyone should stay indoors",
                        "Avoid all outdoor activities completely",
                        "Keep windows and doors sealed shut",
                        "Use high-efficiency air purifiers if available",
                        "Seek immediate medical attention for any respiratory symptoms",
                        "Consider evacuation if conditions persist"
                );
            
            default:
                return Arrays.asList(
                        "Monitor current air quality conditions",
                        "Follow local health department recommendations",
                        "Stay informed about air quality changes"
                );
        }
    }

    private byte[] generateNoDataReport(String city, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Set default date range if not provided
            if (startDate == null) {
                startDate = LocalDateTime.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDateTime.now();
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Title
            document.add(new Paragraph("Air Quality Report")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            // No data message
            document.add(new Paragraph(String.format("No air quality data found for %s", city))
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            document.add(new Paragraph(String.format("Search period: %s to %s", 
                    startDate.format(formatter), endDate.format(formatter)))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(30));

            // Suggestions
            document.add(new Paragraph("Suggestions:")
                    .setFontSize(14)
                    .setBold()
                    .setMarginBottom(10));

            java.util.List<String> suggestions = Arrays.asList(
                    "Verify the city name spelling",
                    "Check if data collection has been set up for this location",
                    "Try a different date range",
                    "Contact support for assistance with data availability"
            );

            com.itextpdf.layout.element.List suggestionList = new com.itextpdf.layout.element.List()
                    .setSymbolIndent(12)
                    .setListSymbol("\u2022");

            for (String suggestion : suggestions) {
                suggestionList.add(new ListItem(suggestion));
            }
            document.add(suggestionList);

            document.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            logger.error("Error generating no-data PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate no-data PDF report: " + e.getMessage(), e);
        }
    }
}