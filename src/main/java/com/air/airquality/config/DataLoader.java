package com.air.airquality.config;

import com.air.airquality.model.AqiData;
import com.air.airquality.model.User;
import com.air.airquality.repository.AqiDataRepository;
import com.air.airquality.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private AqiDataRepository aqiDataRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Check if data already exists
        if (aqiDataRepository.count() == 0) {
            loadSampleData();
        }
        
        if (userRepository.count() == 0) {
            loadSampleUsers();
        }
    }

    private void loadSampleData() {
        List<AqiData> sampleData = Arrays.asList(
            createAqiData("Delhi", 152, 65.4, 98.2, 42.1, 15.6, 2.1, 89.3),
            createAqiData("Mumbai", 89, 34.2, 67.8, 28.5, 12.3, 1.8, 76.4),
            createAqiData("Bangalore", 67, 28.9, 54.3, 24.1, 9.8, 1.2, 65.2),
            createAqiData("Chennai", 78, 32.1, 61.7, 26.8, 11.4, 1.5, 71.9),
            createAqiData("Kolkata", 134, 58.7, 85.4, 38.2, 14.1, 2.3, 82.6),
            createAqiData("New York", 45, 18.2, 32.4, 21.3, 8.7, 1.1, 58.9),
            createAqiData("London", 52, 21.8, 38.9, 19.6, 7.4, 0.9, 62.3),
            createAqiData("Paris", 63, 26.4, 45.7, 23.8, 9.1, 1.3, 68.7),
            createAqiData("Tokyo", 41, 16.9, 29.8, 18.4, 6.2, 0.8, 54.6),
            createAqiData("Beijing", 187, 78.9, 112.6, 48.7, 18.9, 2.8, 94.2)
        );
        
        aqiDataRepository.saveAll(sampleData);
        System.out.println("✅ Sample AQI data loaded successfully!");
    }
    
    private void loadSampleUsers() {
        // Create admin user with known credentials
        User adminUser = new User();
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@airsight.com");
        adminUser.setPassword(passwordEncoder.encode("admin123"));
        adminUser.setPhoneNumber("+1234567890");
        adminUser.setCity("Delhi");
        adminUser.setAlertThreshold(100);
        
        // Create test user for frontend testing
        User testUser = new User();
        testUser.setUsername("test");
        testUser.setEmail("test@airsight.com");
        testUser.setPassword(passwordEncoder.encode("test123"));
        testUser.setPhoneNumber("+1234567891");
        testUser.setCity("Mumbai");
        testUser.setAlertThreshold(80);
        
        userRepository.saveAll(Arrays.asList(adminUser, testUser));
        System.out.println("✅ Sample users loaded successfully!");
        System.out.println("📝 Login credentials:");
        System.out.println("   Admin: username=admin, password=admin123");
        System.out.println("   Test:  username=test, password=test123");
    }
    
    private AqiData createAqiData(String city, int aqi, double pm25, double pm10, double no2, double so2, double co, double o3) {
        AqiData data = new AqiData();
        data.setCity(city);
        data.setAqiValue(aqi);
        data.setPm25(pm25);
        data.setPm10(pm10);
        data.setNo2(no2);
        data.setSo2(so2);
        data.setCo(co);
        data.setO3(o3);
        data.setTimestamp(LocalDateTime.now());
        return data;
    }
}
