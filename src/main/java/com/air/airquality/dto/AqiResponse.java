package com.air.airquality.dto;

import java.time.LocalDateTime;

public class AqiResponse {
    private String city;
    private Integer aqiValue;
    private String aqiCategory;
    private Double pm25;
    private Double pm10;
    private Double no2;
    private Double so2;
    private Double co;
    private Double o3;
    private LocalDateTime timestamp;
    private String category;
    private String description;
    
    // Constructors
    public AqiResponse() {}
    
    public AqiResponse(String city, Integer aqiValue, Double pm25, Double pm10, 
                      Double no2, Double so2, Double co, Double o3, LocalDateTime timestamp) {
        this.city = city;
        this.aqiValue = aqiValue;
        this.aqiCategory = getAqiCategory(aqiValue);
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.no2 = no2;
        this.so2 = so2;
        this.co = co;
        this.o3 = o3;
        this.timestamp = timestamp;
    }
    
    private String getAqiCategory(Integer aqi) {
        if (aqi <= 50) return "Good";
        else if (aqi <= 100) return "Moderate";
        else if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        else if (aqi <= 200) return "Unhealthy";
        else if (aqi <= 300) return "Very Unhealthy";
        else return "Hazardous";
    }
    
    // Getters and Setters
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public Integer getAqiValue() { return aqiValue; }
    public void setAqiValue(Integer aqiValue) { this.aqiValue = aqiValue; }
    
    public String getAqiCategory() { return aqiCategory; }
    public void setAqiCategory(String aqiCategory) { this.aqiCategory = aqiCategory; }
    
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
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
