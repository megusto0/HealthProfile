package com.example.healthprofile.service;

import com.example.healthprofile.entity.HealthData;
import com.example.healthprofile.entity.User;
import com.example.healthprofile.repository.HealthDataRepository;
import com.example.healthprofile.telegram.HealthDataBot;
import org.antlr.v4.runtime.misc.LogManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.data.domain.Pageable;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

import com.example.healthprofile.repository.HealthDataRepository;
import com.example.healthprofile.repository.UserRepository;

@Service
public class HealthDataService {


    private final HealthDataRepository healthDataRepository;
    private final NotificationService notificationService;
    private final UserService userService;


    @Autowired
    public HealthDataService(HealthDataRepository healthDataRepository, @Lazy NotificationService notificationService, UserService userService) {
        this.healthDataRepository = healthDataRepository;
        this.notificationService = notificationService;
        this.userService = userService;
    }



    public List<HealthData> getHealthDataForCurrentUser() {
        User user = userService.getUserDetails();
        Long userId = user.getId();
        return healthDataRepository.findAllByUserIdOrderByDateAscTimeAsc(userId);
    }


    public List<HealthData> getHealthDataForCurrentUserOnDate(Long userId, LocalDate date) {
        return healthDataRepository.findByUserIdAndDate(userId, date);
    }



    public Page<HealthData> getEntriesByUserId(Long userId, Pageable pageable) {
        return healthDataRepository.findByUserId(userId, pageable);
    }

    public void updateEntry(HealthData entry) {
        System.out.println(entry);
        healthDataRepository.save(entry);
    }

    public HealthData getLastHealthDataForCurrentUserOnDateWithGlucose(Long userId, LocalDate date) {
        return healthDataRepository.findFirstByUserIdAndDateAndGlucoseLevelIsNotNullOrderByTimeDesc(userId, date);
    }

    public HealthData getFirstHealthDataForCurrentUserOnDateWithGlucose(Long userId, LocalDate date) {
        return healthDataRepository.findFirstByUserIdAndDateAndGlucoseLevelIsNotNullOrderByTimeAsc(userId, date);
    }

    public List<HealthData> getHealthDataForCurrentUserInRange(Long userId, LocalDate startDate, LocalDate endDate) {
        return healthDataRepository.findByUserIdAndDateBetween(userId, startDate, endDate);
    }
    public void save(HealthData healthData) {
        healthDataRepository.save(healthData);
        notificationService.checkAndNotify(healthData.getUserId());
    }

    public void deleteById(Long id) {
        healthDataRepository.deleteById(id);
    }

    public void parseAndSave(MultipartFile file) throws Exception {
        InputStream inputStream = file.getInputStream();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();
        User user = userService.getUserDetails();

        NodeList nodeList = doc.getElementsByTagName("BG");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        System.out.println("Total nodes to process: " + nodeList.getLength());

        for (int i = 0; i < nodeList.getLength(); i++) {
            try {
                var node = nodeList.item(i);
                var dateStr = node.getAttributes().getNamedItem("Dt").getNodeValue();
                var timeStr = node.getAttributes().getNamedItem("Tm").getNodeValue();
                var glucoseLevel = Float.parseFloat(node.getAttributes().getNamedItem("Gl").getNodeValue());

                LocalDate date = LocalDate.parse(dateStr, dateFormatter);
                LocalTime time = LocalTime.parse(timeStr, timeFormatter);

                HealthData healthData = new HealthData();
                healthData.setDate(date);
                healthData.setTime(time);
                healthData.setGlucoseLevel((double) glucoseLevel);
                healthData.setUserId(user.getId());


                LogManager repository;
                healthDataRepository.save(healthData);
            } catch (Exception e) {
                System.out.println("Error processing node " + (i + 1) + ": " + e.getMessage());
            }
        }
    }

    public Map<LocalDate, Double> calculateAverageGlucoseLevels(List<HealthData> healthDataList) {
        Map<LocalDate, List<Double>> weeklyGlucoseMap = new HashMap<>();

        for (HealthData data : healthDataList) {
            if (data.getGlucoseLevel() != null) {
                LocalDate date = data.getDate();
                LocalDate weekStartDate = date.with(WeekFields.of(Locale.getDefault()).getFirstDayOfWeek());
                if (weekStartDate.getDayOfWeek() == DayOfWeek.MONDAY) {
                    weekStartDate = weekStartDate.plusDays(1);
                }
                weeklyGlucoseMap.computeIfAbsent(weekStartDate, k -> new ArrayList<>()).add(data.getGlucoseLevel());
            }
        }

        Map<LocalDate, Double> weeklyAverages = new TreeMap<>(); // Используем TreeMap для сортировки по дате
        for (Map.Entry<LocalDate, List<Double>> entry : weeklyGlucoseMap.entrySet()) {
            LocalDate weekStartDate = entry.getKey();
            List<Double> glucoseLevels = entry.getValue();
            double average = glucoseLevels.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            weeklyAverages.put(weekStartDate, average);
        }

        return weeklyAverages;

    }
}
