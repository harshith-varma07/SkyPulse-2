package com.air.airquality.util;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe LRU Cache with Time-To-Live (TTL) functionality
 * Optimized for high-performance caching of AQI data
 */
public class LRUCacheWithTTL<K, V> {
    
    private final int maxSize;
    private final long ttlMillis;
    private final Map<K, CacheEntry<V>> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private static class CacheEntry<V> {
        final V value;
        final long expiryTime;
        
        CacheEntry(V value, long ttlMillis) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMillis;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
    
    public LRUCacheWithTTL(int maxSize, long ttlMillis) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        // LinkedHashMap with access-order for LRU behavior
        this.cache = new LinkedHashMap<K, CacheEntry<V>>(maxSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > maxSize;
            }
        };
    }
    
    public V get(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                // Need to remove expired entry
                if (entry != null) {
                    lock.readLock().unlock();
                    lock.writeLock().lock();
                    try {
                        cache.remove(key);
                        return null;
                    } finally {
                        lock.readLock().lock();
                        lock.writeLock().unlock();
                    }
                }
                return null;
            }
            return entry.value;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry<>(value, ttlMillis));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void remove(K key) {
        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public int size() {
        lock.readLock().lock();
        try {
            // Clean expired entries during size check
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    // Cleanup expired entries (should be called periodically)
    public void cleanup() {
        lock.writeLock().lock();
        try {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
