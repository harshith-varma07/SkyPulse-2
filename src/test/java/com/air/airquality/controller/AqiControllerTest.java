package com.air.airquality.controller;

import com.air.airquality.services.AqiService;
import com.air.airquality.services.OpenAQService;
import com.air.airquality.model.AqiData;
import com.air.airquality.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AqiController.class)
@Import(TestSecurityConfig.class)
public class AqiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AqiService aqiService;
    
    @MockBean
    private OpenAQService openAQService;

    @Test
    public void testGetCitiesEndpoint() throws Exception {
        // Mock the service response
        List<String> mockCities = Arrays.asList("Delhi", "Mumbai", "Bangalore");
        when(openAQService.getAvailableCities()).thenReturn(mockCities);
        
        mockMvc.perform(get("/api/aqi/cities"))
               .andExpect(status().isOk());
    }

    @Test
    public void testGetCurrentAqiEndpoint() throws Exception {
        // Mock the service response
        AqiData mockData = new AqiData("Delhi", 85, 45.0, 65.0, 30.0, 15.0, 1.2, 80.0);
        mockData.setTimestamp(LocalDateTime.now());
        when(openAQService.getCurrentAqiData(anyString())).thenReturn(mockData);
        when(openAQService.getAqiCategory(85)).thenReturn("Moderate");
        when(openAQService.getAqiDescription(85)).thenReturn("Air quality is acceptable for most people.");
        
        mockMvc.perform(get("/api/aqi/current/Delhi"))
               .andExpect(status().isOk());
    }
}
