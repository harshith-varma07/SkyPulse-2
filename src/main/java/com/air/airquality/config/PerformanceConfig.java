package com.air.airquality.config;

import com.air.airquality.util.LRUCacheWithTTL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Performance optimization configuration for thread pools and caching
 */
@Configuration
public class PerformanceConfig {

    // Thread pool configuration
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;
    private static final int QUEUE_CAPACITY = 100;
    
    // Cache configuration
    private static final int CACHE_MAX_SIZE = 1000;
    private static final long CACHE_TTL_MINUTES = 30;
    private static final int ANALYTICS_CACHE_SIZE = 500;
    private static final long ANALYTICS_CACHE_TTL_MINUTES = 60;

    /**
     * Async task executor for general parallel processing
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("AQI-Async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor service for analytics processing
     */
    @Bean(name = "analyticsExecutor")
    public ExecutorService analyticsExecutor() {
        return Executors.newFixedThreadPool(CORE_POOL_SIZE, r -> {
            Thread t = new Thread(r, "Analytics-Thread-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Scheduled executor for background tasks
     */
    @Bean(name = "scheduledExecutor")
    public ScheduledExecutorService scheduledExecutor() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Scheduled-Task-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * LRU Cache for AQI data with TTL
     */
    @Bean(name = "aqiDataCache")
    public LRUCacheWithTTL<String, Object> aqiDataCache() {
        return new LRUCacheWithTTL<>(CACHE_MAX_SIZE, CACHE_TTL_MINUTES * 60 * 1000L);
    }

    /**
     * LRU Cache for analytics data with longer TTL
     */
    @Bean(name = "analyticsCache")
    public LRUCacheWithTTL<String, Object> analyticsCache() {
        return new LRUCacheWithTTL<>(ANALYTICS_CACHE_SIZE, ANALYTICS_CACHE_TTL_MINUTES * 60 * 1000L);
    }

    /**
     * Circuit breaker configuration bean
     */
    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.builder()
                .failureThreshold(5)
                .recoveryTimeout(30000L) // 30 seconds
                .retryAttempts(3)
                .build();
    }

    /**
     * Circuit breaker configuration data class
     */
    public static class CircuitBreakerConfig {
        private final int failureThreshold;
        private final long recoveryTimeout;
        private final int retryAttempts;

        private CircuitBreakerConfig(Builder builder) {
            this.failureThreshold = builder.failureThreshold;
            this.recoveryTimeout = builder.recoveryTimeout;
            this.retryAttempts = builder.retryAttempts;
        }

        public static Builder builder() {
            return new Builder();
        }

        public int getFailureThreshold() { return failureThreshold; }
        public long getRecoveryTimeout() { return recoveryTimeout; }
        public int getRetryAttempts() { return retryAttempts; }

        public static class Builder {
            private int failureThreshold = 5;
            private long recoveryTimeout = 30000L;
            private int retryAttempts = 3;

            public Builder failureThreshold(int failureThreshold) {
                this.failureThreshold = failureThreshold;
                return this;
            }

            public Builder recoveryTimeout(long recoveryTimeout) {
                this.recoveryTimeout = recoveryTimeout;
                return this;
            }

            public Builder retryAttempts(int retryAttempts) {
                this.retryAttempts = retryAttempts;
                return this;
            }

            public CircuitBreakerConfig build() {
                return new CircuitBreakerConfig(this);
            }
        }
    }
}
