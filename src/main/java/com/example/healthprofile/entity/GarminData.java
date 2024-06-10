package com.example.healthprofile.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
@Getter
@Entity
@Table(name = "garmin_data")
@Setter
public class GarminData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "time", nullable = false)
    private LocalTime time;

    @Column(name = "heart_rate", nullable = false)
    private Integer heartRate;

    @Column(name = "user_id")
    private Long userId;
}
