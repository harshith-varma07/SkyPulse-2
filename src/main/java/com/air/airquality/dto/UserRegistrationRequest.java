package com.air.airquality.dto;

public class UserRegistrationRequest {
    private String username;
    private String email;
    private String password;
    private String phoneNumber;
    private String city;
    private Integer alertThreshold;
    
    // Constructors, getters, and setters
    public UserRegistrationRequest() {}
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public Integer getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(Integer alertThreshold) { this.alertThreshold = alertThreshold; }
}