package com.air.airquality.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_alerts")
public class UserAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    private String city;
    
    @Column(name = "aqi_value")
    private Integer aqiValue;
    
    @Column(name = "threshold_exceeded")
    private Integer thresholdExceeded;
    
    @Column(name = "alert_sent")
    private Boolean alertSent = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Constructors, getters, and setters
    public UserAlert() {
        this.createdAt = LocalDateTime.now();
    }
    
    public UserAlert(User user, String city, Integer aqiValue, Integer thresholdExceeded) {
        this.user = user;
        this.city = city;
        this.aqiValue = aqiValue;
        this.thresholdExceeded = thresholdExceeded;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public Integer getAqiValue() { return aqiValue; }
    public void setAqiValue(Integer aqiValue) { this.aqiValue = aqiValue; }
    
    public Integer getThresholdExceeded() { return thresholdExceeded; }
    public void setThresholdExceeded(Integer thresholdExceeded) { this.thresholdExceeded = thresholdExceeded; }
    
    public Boolean getAlertSent() { return alertSent; }
    public void setAlertSent(Boolean alertSent) { this.alertSent = alertSent; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}