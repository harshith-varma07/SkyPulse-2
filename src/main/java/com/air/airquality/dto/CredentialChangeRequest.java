package com.air.airquality.dto;

public class CredentialChangeRequest {
    private String currentPassword; // Required for verification
    private String newPassword; // Optional - only if user wants to change password
    private String email; // Optional - only if user wants to change email
    private String phoneNumber; // Optional - only if user wants to change phone
    private String city; // Optional - only if user wants to change city
    private Integer alertThreshold; // Optional - only if user wants to change threshold
    
    public CredentialChangeRequest() {}
    
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public Integer getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(Integer alertThreshold) { this.alertThreshold = alertThreshold; }
}