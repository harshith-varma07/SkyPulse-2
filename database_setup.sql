-- Complete Air Quality Monitoring Database Schema
-- Run this script to set up the complete database

CREATE DATABASE IF NOT EXISTS air_quality_monitoring;
USE air_quality_monitoring;

-- Users table with all required fields
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    city VARCHAR(100),
    alert_threshold INT DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_city (city)
);

-- AQI Data table with optimized indexes and partitioning strategy
CREATE TABLE IF NOT EXISTS aqi_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    aqi_value INT NOT NULL,
    pm25 DECIMAL(10,2),
    pm10 DECIMAL(10,2),
    no2 DECIMAL(10,2),
    so2 DECIMAL(10,2),
    co DECIMAL(10,2),
    o3 DECIMAL(10,2),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Optimized indexes for query patterns
    INDEX idx_city_timestamp (city, timestamp DESC),  -- Composite index for city + time queries
    INDEX idx_timestamp_desc (timestamp DESC),        -- For time-range queries
    INDEX idx_city_aqi (city, aqi_value),            -- For city-specific AQI queries
    INDEX idx_timestamp_aqi (timestamp, aqi_value),   -- For temporal AQI analysis
    
    -- Covering indexes for common analytics queries
    INDEX idx_city_time_aqi_pm (city, timestamp, aqi_value, pm25, pm10), -- Analytics covering index
    
    -- Full-text search index for city names (if needed)
    FULLTEXT INDEX idx_city_fulltext (city)
    
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  -- Optimize for time-series data
  ROW_FORMAT=COMPRESSED 
  KEY_BLOCK_SIZE=8;

-- User Alerts table for tracking alert history
CREATE TABLE IF NOT EXISTS user_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    city VARCHAR(100) NOT NULL,
    aqi_value INT NOT NULL,
    threshold_value INT NOT NULL,
    alert_sent BOOLEAN DEFAULT FALSE,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_timestamp (timestamp)
);

-- Insert some sample cities to get started
INSERT INTO aqi_data (city, aqi_value, pm25, pm10, no2, so2, co, o3) VALUES
('Delhi', 152, 65.4, 98.2, 42.1, 15.6, 2.1, 89.3),
('Mumbai', 89, 34.2, 67.8, 28.5, 12.3, 1.8, 76.4),
('Bangalore', 67, 28.9, 54.3, 24.1, 9.8, 1.2, 65.2),
('Chennai', 78, 32.1, 61.7, 26.8, 11.4, 1.5, 71.9),
('Kolkata', 134, 58.7, 85.4, 38.2, 14.1, 2.3, 82.6),
('New York', 45, 18.2, 32.4, 21.3, 8.7, 1.1, 58.9),
('London', 52, 21.8, 38.9, 19.6, 7.4, 0.9, 62.3),
('Paris', 63, 26.4, 45.7, 23.8, 9.1, 1.3, 68.7),
('Tokyo', 41, 16.9, 29.8, 18.4, 6.2, 0.8, 54.6),
('Beijing', 187, 78.9, 112.6, 48.7, 18.9, 2.8, 94.2)
ON DUPLICATE KEY UPDATE
aqi_value = VALUES(aqi_value),
pm25 = VALUES(pm25),
pm10 = VALUES(pm10),
timestamp = CURRENT_TIMESTAMP;

-- Create a sample admin user (password: admin123)
INSERT INTO users (username, email, password, phone_number, city, alert_threshold) VALUES
('admin', 'admin@airsight.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM.lbESZbYrHYgdHsLny', '+91 9966383848', 'Delhi', 100)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

COMMIT;
