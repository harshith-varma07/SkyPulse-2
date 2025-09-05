package com.air.airquality.services;

import com.air.airquality.model.AqiData;
import com.air.airquality.repository.AqiDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

    @Mock
    private AqiDataRepository aqiDataRepository;

    @InjectMocks
    private PdfGenerationService pdfGenerationService;

    @Test
    void testGenerateAirQualityReport_WithData() {
        // Given
        String city = "Test City";
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        
        List<AqiData> mockData = Arrays.asList(
            createMockAqiData(city, 50, 35.0, 25.0),
            createMockAqiData(city, 75, 45.0, 30.0),
            createMockAqiData(city, 100, 55.0, 35.0)
        );
        
        when(aqiDataRepository.findByCityAndTimestampBetween(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(mockData);

        // When
        byte[] result = pdfGenerationService.generateAirQualityReport(city, startDate, endDate);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // PDF files start with %PDF
        assertTrue(new String(result).startsWith("%PDF"));
    }

    @Test
    void testGenerateAirQualityReport_NoData() {
        // Given
        String city = "Empty City";
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        
        when(aqiDataRepository.findByCityAndTimestampBetween(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Arrays.asList());

        // When
        byte[] result = pdfGenerationService.generateAirQualityReport(city, startDate, endDate);

        // Then
        assertNotNull(result);
        assertTrue(result.length > 0);
        // Should still generate a "no data" PDF
        assertTrue(new String(result).startsWith("%PDF"));
    }

    private AqiData createMockAqiData(String city, int aqiValue, double pm25, double pm10) {
        AqiData data = new AqiData();
        data.setId((long) Math.random() * 1000);
        data.setCity(city);
        data.setAqiValue(aqiValue);
        data.setPm25(pm25);
        data.setPm10(pm10);
        data.setNo2(15.0);
        data.setSo2(8.0);
        data.setCo(0.5);
        data.setO3(75.0);
        data.setTimestamp(LocalDateTime.now().minusHours((int)(Math.random() * 24)));
        return data;
    }
}