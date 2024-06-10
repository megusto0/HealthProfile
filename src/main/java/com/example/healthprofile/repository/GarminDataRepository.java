package com.example.healthprofile.repository;

import com.example.healthprofile.entity.GarminData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GarminDataRepository extends JpaRepository<GarminData, Long> {

    @Query("SELECT g FROM GarminData g WHERE g.userId = :userId AND g.date = :date")
    List<GarminData> findHeartRateDataForCurrentUserOnDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT MAX(g.date) FROM GarminData g WHERE g.userId = :userId ORDER BY g.date ASC, g.time ASC")
    LocalDate findLastHeartRateDateForCurrentUser(@Param("userId") Long userId);

    @Query("SELECT MAX(g.date || 'T' || g.time) FROM GarminData g WHERE g.userId = ?1")
    String findMaxTimestampStringByUserId(long userId);

}
