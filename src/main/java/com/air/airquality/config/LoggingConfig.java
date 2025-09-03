package com.air.airquality.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
@Configuration
public class LoggingConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
    
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
}