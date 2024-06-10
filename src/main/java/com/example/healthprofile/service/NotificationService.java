package com.example.healthprofile.service;

import com.example.healthprofile.entity.HealthData;
import com.example.healthprofile.repository.HealthDataRepository;
import com.example.healthprofile.telegram.HealthDataBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final HealthDataBot healthDataBot;
    private final HealthDataRepository healthDataRepository;

    private static final double HYPERGLYCEMIA_THRESHOLD = 10.0;
    private static final double HYPOGLYCEMIA_THRESHOLD = 4;

    private static final LocalTime MORNING_START = LocalTime.of(6, 0);
    private static final LocalTime MORNING_END = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(18, 0);
    private static final LocalTime EVENING_START = LocalTime.of(18, 0);
    private static final LocalTime EVENING_END = LocalTime.of(23, 59);
    private static final LocalTime NIGHT_START = LocalTime.of(0, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    @Autowired
    public NotificationService(@Lazy HealthDataBot healthDataBot, HealthDataRepository healthDataRepository) {
        this.healthDataBot = healthDataBot;
        this.healthDataRepository = healthDataRepository;
    }

    public void checkAndNotify(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);
        List<HealthData> recentData = healthDataRepository.findByUserIdAndDateBetween(userId, threeDaysAgo, today);

        checkPeriod(recentData, userId, MORNING_START, MORNING_END, "06:00 - 12:00");
        checkPeriod(recentData, userId, AFTERNOON_START, AFTERNOON_END, "12:00 - 18:00");
        checkPeriod(recentData, userId, EVENING_START, EVENING_END, "18:00 - 00:00");
        checkPeriod(recentData, userId, NIGHT_START, NIGHT_END, "00:00 - 06:00");
    }

    private void checkPeriod(List<HealthData> recentData, Long userId, LocalTime periodStart, LocalTime periodEnd, String periodLabel) {
        List<HealthData> periodData = recentData.stream()
                .filter(data -> data.getGlucoseLevel() != null) // Добавляем проверку на null
                .filter(data -> !data.getTime().isBefore(periodStart) && data.getTime().isBefore(periodEnd))
                .toList();

        long hyperglycemiaCount = periodData.stream()
                .filter(data -> data.getGlucoseLevel() > HYPERGLYCEMIA_THRESHOLD)
                .count();

        long hypoglycemiaCount = periodData.stream()
                .filter(data -> data.getGlucoseLevel() < HYPOGLYCEMIA_THRESHOLD)
                .count();

        if (hyperglycemiaCount >= 3) {
            sendNotification(userId, periodLabel, "гипергликемия");
        }

        if (hypoglycemiaCount >= 3) {
            sendNotification(userId, periodLabel, "гипогликемия");
        }
    }

    private void sendNotification(Long userId, String periodLabel, String condition) {
        String message = String.format("Замечена %s в период %s 3 дня подряд. Возможно стоит пересмотреть настройки базального инсулина", condition, periodLabel);
        healthDataBot.sendNotification(userId, message);
    }
}