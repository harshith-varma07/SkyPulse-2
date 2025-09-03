package com.air.airquality.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "aqi_data")
public class AqiData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String city;
    
    @Column(name = "aqi_value", nullable = false)
    private Integer aqiValue;
    
    private Double pm25;
    private Double pm10;
    private Double no2;
    private Double so2;
    private Double co;
    private Double o3;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private String source = "openaq";
    
    // Constructors
    public AqiData() {
        this.timestamp = LocalDateTime.now();
    }
    
    public AqiData(String city, Integer aqiValue, Double pm25, Double pm10, 
                   Double no2, Double so2, Double co, Double o3) {
        this.city = city;
        this.aqiValue = aqiValue;
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.no2 = no2;
        this.so2 = so2;
        this.co = co;
        this.o3 = o3;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public Integer getAqiValue() { return aqiValue; }
    public void setAqiValue(Integer aqiValue) { this.aqiValue = aqiValue; }
    
    public Double getPm25() { return pm25; }
    public void setPm25(Double pm25) { this.pm25 = pm25; }
    
    public Double getPm10() { return pm10; }
    public void setPm10(Double pm10) { this.pm10 = pm10; }
    
    public Double getNo2() { return no2; }
    public void setNo2(Double no2) { this.no2 = no2; }
    
    public Double getSo2() { return so2; }
    public void setSo2(Double so2) { this.so2 = so2; }
    
    public Double getCo() { return co; }
    public void setCo(Double co) { this.co = co; }
    
    public Double getO3() { return o3; }
    public void setO3(Double o3) { this.o3 = o3; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}