package com.air;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.air.airquality.AirQualityMonitoringApplication;

@SpringBootTest(classes = AirQualityMonitoringApplication.class)
@ActiveProfiles("test")
class AirQualityMonitoringApplicationTest {
    @Test
    void contextLoads() {
        // This test verifies that the application context loads successfully
        // with the test profile that uses H2 in-memory database
    }
}
