package com.air.airquality.repository;

import com.air.airquality.model.AqiData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AqiDataRepository extends JpaRepository<AqiData, Long> {
    
    // Optimized query using compound index on city and timestamp
    @Query("SELECT a FROM AqiData a WHERE a.city = :city ORDER BY a.timestamp DESC")
    Optional<AqiData> findTopByCityOrderByTimestampDesc(@Param("city") String city);
    
    // Efficient paginated query for large datasets
    @Query("SELECT a FROM AqiData a WHERE a.city = :city AND a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    Page<AqiData> findByCityAndTimestampBetweenOrderByTimestampDesc(
        @Param("city") String city, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    
    // Non-paginated version for analytics
    @Query("SELECT a FROM AqiData a WHERE a.city = :city AND a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<AqiData> findByCityAndTimestampBetween(@Param("city") String city, 
                                               @Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    // Optimized distinct cities query with limit
    @Query("SELECT DISTINCT a.city FROM AqiData a ORDER BY a.city LIMIT 50")
    List<String> findDistinctCities();
    
    // City existence check using EXISTS for better performance
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AqiData a WHERE a.city = :city")
    boolean existsByCity(@Param("city") String city);
    
    // Batch cleanup with index optimization
    @Modifying
    @Query("DELETE FROM AqiData a WHERE a.timestamp < :cutoffDate")
    void deleteOldData(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Optimized latest data query for dashboard using window functions
    @Query("""
        SELECT a FROM AqiData a WHERE (a.city, a.timestamp) IN (
            SELECT a2.city, MAX(a2.timestamp) 
            FROM AqiData a2 
            GROUP BY a2.city
        ) ORDER BY a.city
        """)
    List<AqiData> findLatestDataForAllCities();
    
    // Efficient aggregation queries for statistics
    @Query("SELECT AVG(a.aqiValue) FROM AqiData a WHERE a.city = :city AND a.timestamp BETWEEN :startDate AND :endDate")
    Optional<Double> findAverageAqiByCity(@Param("city") String city, 
                                         @Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT MIN(a.aqiValue) FROM AqiData a WHERE a.city = :city AND a.timestamp BETWEEN :startDate AND :endDate")
    Optional<Integer> findMinAqiByCity(@Param("city") String city, 
                                      @Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT MAX(a.aqiValue) FROM AqiData a WHERE a.city = :city AND a.timestamp BETWEEN :startDate AND :endDate")
    Optional<Integer> findMaxAqiByCity(@Param("city") String city, 
                                      @Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    // Data availability methods for analytics with optimized indexes
    @Query("SELECT MIN(a.timestamp) FROM AqiData a WHERE a.city = :city")
    Optional<LocalDateTime> findOldestDateByCity(@Param("city") String city);
    
    @Query("SELECT MAX(a.timestamp) FROM AqiData a WHERE a.city = :city")
    Optional<LocalDateTime> findNewestDateByCity(@Param("city") String city);
    
    @Query("SELECT MIN(a.timestamp) FROM AqiData a")
    Optional<LocalDateTime> findOldestDate();
    
    @Query("SELECT MAX(a.timestamp) FROM AqiData a")
    Optional<LocalDateTime> findNewestDate();
    
    // Optimized count queries
    @Query("SELECT COUNT(a) FROM AqiData a WHERE a.city = :city")
    long countByCity(@Param("city") String city);
    
    // Bulk insert optimization hint
    @Query("SELECT COUNT(a) FROM AqiData a WHERE a.timestamp BETWEEN :startDate AND :endDate")
    long countByTimestampBetween(@Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    // Time-series data with sampling for large datasets
    @Query(value = """
        SELECT * FROM aqi_data a 
        WHERE a.city = :city 
        AND a.timestamp BETWEEN :startDate AND :endDate 
        AND MOD(UNIX_TIMESTAMP(a.timestamp), :sampleRate) = 0
        ORDER BY a.timestamp DESC
        """, nativeQuery = true)
    List<AqiData> findSampledDataByCity(@Param("city") String city,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate,
                                       @Param("sampleRate") int sampleRate);
    
    // Recent data for hot cache loading
    @Query("SELECT a FROM AqiData a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AqiData> findRecentData(@Param("since") LocalDateTime since);
}