package com.air.airquality.repository;

import com.air.airquality.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    
    // Check if user exists by username or email (for validation)
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.city = :city AND u.alertThreshold <= :aqiValue")
    List<User> findUsersForAlert(@Param("city") String city, @Param("aqiValue") Integer aqiValue);
}