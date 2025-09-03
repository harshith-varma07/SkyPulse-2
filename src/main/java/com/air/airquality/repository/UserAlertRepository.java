package com.air.airquality.repository;

import com.air.airquality.model.UserAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {
    
    List<UserAlert> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    UserAlert findByIdAndUserId(Long alertId, Long userId);
    
    @Query("SELECT ua FROM UserAlert ua WHERE ua.user.id = :userId AND ua.createdAt >= :startDate")
    List<UserAlert> findByUserIdAndTimestampAfter(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(ua) FROM UserAlert ua WHERE ua.user.id = :userId AND ua.alertSent = true")
    Long countSentAlertsByUserId(@Param("userId") Long userId);
}