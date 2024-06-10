package com.example.healthprofile.repository;

import com.example.healthprofile.entity.HealthData;
import com.example.healthprofile.entity.User;
import com.example.healthprofile.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HealthDataRepository extends JpaRepository<HealthData, Long> {
    @Query("SELECT h FROM HealthData h WHERE h.userId = ?1 ORDER BY h.date ASC, h.time ASC")
    List<HealthData> findAllByUserIdOrderByDateAscTimeAsc(Long userId);

    @Query("SELECT h FROM HealthData h WHERE h.userId = :userId AND h.date = :date ORDER BY h.date ASC, h.time ASC")
    List<HealthData> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    @Query("SELECT h FROM HealthData h WHERE h.userId = :userId ORDER BY h.date DESC, h.time DESC")
    Page<HealthData> findByUserId(Long userId, Pageable pageable);

    HealthData findFirstByUserIdAndDateAndGlucoseLevelIsNotNullOrderByTimeDesc(Long userId, LocalDate date);
    HealthData findFirstByUserIdAndDateAndGlucoseLevelIsNotNullOrderByTimeAsc(Long userId, LocalDate date);
    List<HealthData> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

}

