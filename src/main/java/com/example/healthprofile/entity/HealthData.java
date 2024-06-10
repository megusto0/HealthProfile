package com.example.healthprofile.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Setter
@Entity
@Table(name = "health_data")
@Data
@NoArgsConstructor
public class HealthData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Getter
    @Column(name = "time", nullable = false)
    private LocalTime time;

    @Getter
    @Column(name = "glucose_level")
    private Double glucoseLevel;

    @Getter
    @Column(name = "insulin_dose")
    private Double insulinDose;

    @Getter
    @Column(name = "food_intake")
    private String foodIntake;

    @Getter
    @Column(name = "carbohydrates")
    private Double carbohydrates;

    @Getter
    @Column(name = "userid")
    private Long userId;

    @Getter
    @Column(name = "catheterchange")
    private Boolean catheterChange;

    @Getter
    @Column(name = "ampoulechange")
    private Boolean ampouleChange;

}

