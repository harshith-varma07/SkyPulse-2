package com.air.airquality.dto;

import java.time.LocalDateTime;

public class DataAvailabilityInfo {
    private LocalDateTime oldestDate;
    private LocalDateTime newestDate;
    private long recordCount;
    private String city;
    private boolean hasData;
    
    // Constructors
    public DataAvailabilityInfo() {}
    
    public DataAvailabilityInfo(LocalDateTime oldestDate, LocalDateTime newestDate, long recordCount, String city) {
        this.oldestDate = oldestDate;
        this.newestDate = newestDate;
        this.recordCount = recordCount;
        this.city = city;
        this.hasData = oldestDate != null && recordCount > 0;
    }
    
    // Getters and Setters
    public LocalDateTime getOldestDate() {
        return oldestDate;
    }
    
    public void setOldestDate(LocalDateTime oldestDate) {
        this.oldestDate = oldestDate;
        updateHasData();
    }
    
    public LocalDateTime getNewestDate() {
        return newestDate;
    }
    
    public void setNewestDate(LocalDateTime newestDate) {
        this.newestDate = newestDate;
    }
    
    public long getRecordCount() {
        return recordCount;
    }
    
    public void setRecordCount(long recordCount) {
        this.recordCount = recordCount;
        updateHasData();
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public boolean isHasData() {
        return hasData;
    }
    
    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }
    
    private void updateHasData() {
        this.hasData = this.oldestDate != null && this.recordCount > 0;
    }
    
    // Helper method to get data period in days
    public long getDataPeriodInDays() {
        if (oldestDate != null && newestDate != null) {
            return java.time.temporal.ChronoUnit.DAYS.between(oldestDate, newestDate);
        }
        return 0;
    }
    
    @Override
    public String toString() {
        return "DataAvailabilityInfo{" +
                "city='" + city + '\'' +
                ", oldestDate=" + oldestDate +
                ", newestDate=" + newestDate +
                ", recordCount=" + recordCount +
                ", hasData=" + hasData +
                ", dataPeriodDays=" + getDataPeriodInDays() +
                '}';
    }
}
