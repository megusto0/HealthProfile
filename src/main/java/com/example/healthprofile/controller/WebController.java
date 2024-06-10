package com.example.healthprofile.controller;

import com.example.healthprofile.entity.GarminData;
import com.example.healthprofile.entity.HealthData;
import com.example.healthprofile.entity.User;
import com.example.healthprofile.service.GarminDataService;
import com.example.healthprofile.service.HealthDataService;
import com.example.healthprofile.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class WebController {


    @Autowired
    private HealthDataService healthDataService;

    @Autowired
    private GarminDataService garminDataService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String home(Model model,
                       @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        if (startDate == null) {
            startDate = LocalDate.now();
        }

        Long userId = getCurrentUserId();
        List<HealthData> healthDataList = healthDataService.getHealthDataForCurrentUserOnDate(userId, startDate);
        List<GarminData> heartRateDataList = garminDataService.getHeartRateDataForCurrentUserOnDate(userId, startDate);
        List<HealthData> fullHealthDataList = healthDataService.getHealthDataForCurrentUser();

        String healthDataJson = convertToJson(healthDataList);
        String heartRateDataJson = convertToJson(heartRateDataList);

        LocalDate endDate = startDate.plusDays(1);
        LocalDate weekAgo = startDate.minusWeeks(1);
        LocalDate twoWeeksAgo = startDate.minusWeeks(2);

        Map<String, Double> week1Stats = calculateWeeklyStats(fullHealthDataList.stream()
                .filter(data -> !data.getDate().isBefore(weekAgo) && !data.getDate().isAfter(endDate))
                .collect(Collectors.toList()));

        Map<String, Double> week2Stats = calculateWeeklyStats(fullHealthDataList.stream()
                .filter(data -> !data.getDate().isBefore(twoWeeksAgo) && !data.getDate().isAfter(weekAgo))
                .collect(Collectors.toList()));

        Map<LocalDate, Double> averageGlucoseLevels = healthDataService.calculateAverageGlucoseLevels(fullHealthDataList);

        String averageGlucoseLevelsJson = convertToJson(averageGlucoseLevels);

        model.addAttribute("healthDataJson", healthDataJson);
        model.addAttribute("heartRateDataJson", heartRateDataJson);
        model.addAttribute("startDate", startDate);
        model.addAttribute("week1Stats", convertToJson(week1Stats));
        model.addAttribute("week2Stats", convertToJson(week2Stats));
        model.addAttribute("averageGlucoseLevelsJson", averageGlucoseLevelsJson);

        return "index";
    }

    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> getData(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = getCurrentUserId();
        List<HealthData> healthData = healthDataService.getHealthDataForCurrentUserOnDate(userId, date);
        List<GarminData> heartRateData = garminDataService.getHeartRateDataForCurrentUserOnDate(userId, date);

        LocalDate prevDate = date.minusDays(1);
        LocalDate nextDate = date.plusDays(1);

        HealthData prevDayLastEntry = healthDataService.getLastHealthDataForCurrentUserOnDateWithGlucose(userId, prevDate);
        HealthData nextDayFirstEntry = healthDataService.getFirstHealthDataForCurrentUserOnDateWithGlucose(userId, nextDate);

        if (prevDayLastEntry != null) {
            healthData.add(0, prevDayLastEntry);
        }

        if (nextDayFirstEntry != null) {
            healthData.add(nextDayFirstEntry);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("healthData", healthData);
        data.put("heartRateData", heartRateData);
        data.put("week1Stats", calculateWeeklyStats(healthDataService.getHealthDataForCurrentUserInRange(userId, date.minusWeeks(1), date)));
        data.put("week2Stats", calculateWeeklyStats(healthDataService.getHealthDataForCurrentUserInRange(userId, date.minusWeeks(2), date.minusWeeks(1))));

        Map<LocalDate, Double> averageGlucoseLevels = healthDataService.calculateAverageGlucoseLevels(healthDataService.getHealthDataForCurrentUser());
        data.put("averageGlucoseLevels", averageGlucoseLevels);

        return data;
    }

    private Long getCurrentUserId() {
        User user = userService.getUserDetails();
        return user.getId();
    }

    private String convertToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    private Map<String, Double> calculateWeeklyStats(List<HealthData> healthDataList) {
        double avgGlucose = healthDataList.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .mapToDouble(HealthData::getGlucoseLevel)
                .average()
                .orElse(0);
        avgGlucose = Math.round(avgGlucose * 10.0) / 10.0;

        Map<LocalDate, Double> dailyInsulinSums = healthDataList.stream()
                .filter(data -> data.getInsulinDose() != null)
                .collect(Collectors.groupingBy(HealthData::getDate, Collectors.summingDouble(HealthData::getInsulinDose)));

        double avgInsulin = dailyInsulinSums.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        avgInsulin = Math.round(avgInsulin * 10.0) / 10.0;

        double maxGlucose = healthDataList.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .mapToDouble(HealthData::getGlucoseLevel)
                .max()
                .orElse(0);
        maxGlucose = Math.round(maxGlucose * 10.0) / 10.0;

        double minGlucose = healthDataList.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .mapToDouble(HealthData::getGlucoseLevel)
                .min()
                .orElse(0);
        minGlucose = Math.round(minGlucose * 10.0) / 10.0;

        return Map.of(
                "avgGlucose", avgGlucose,
                "avgInsulin", avgInsulin,
                "maxGlucose", maxGlucose,
                "minGlucose", minGlucose
        );
    }

    @GetMapping("/profile")
    public String showUserProfile(Model model) {
        User user = userService.getUserDetails();
        model.addAttribute("id", user.getId());
        model.addAttribute("name", user.getUsername());
        model.addAttribute("email", user.getEmail());
        return "profile";
    }

    @GetMapping("/glucose")
    public String glucose(Model model) {
        List<HealthData> hd = healthDataService.getHealthDataForCurrentUser();
        model.addAttribute("glucoseRecords", hd);
        model.addAttribute("currentLevel", "120 mg/dL");
        model.addAttribute("title", "Уровень глюкозы");
        return "glucose";
    }

    @GetMapping("/history")
    public String getDiary(@RequestParam(defaultValue = "1") int page, Model model) {
        Long userId = getCurrentUserId();
        int pageSize = 16;
        Page<HealthData> pagedResult = healthDataService.getEntriesByUserId(userId, PageRequest.of(page - 1, pageSize));

        model.addAttribute("pagedEntries", pagedResult.getContent());
        model.addAttribute("totalPages", pagedResult.getTotalPages());
        model.addAttribute("currentPage", page);
        return "history";
    }

    @PostMapping("/delete")
    public String deleteEntry(@RequestParam Long id) {
        healthDataService.deleteById(id);
        return "redirect:/history";
    }

    @PostMapping("/edit")
    public String editEntry(@ModelAttribute HealthData entry) {
        healthDataService.updateEntry(entry);
        return "redirect:/history";
    }

    @PostMapping("/add")
    public String addEntry(@ModelAttribute HealthData healthData) {
        healthData.setUserId(getCurrentUserId());
        healthDataService.save(healthData);
        return "redirect:/history";
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        return "settings";
    }

    @PostMapping("/upload")
    public String uploadAndParseFile(@RequestParam("file") MultipartFile file, Model model) {

        try {
            healthDataService.parseAndSave(file);
            model.addAttribute("message", "Данные успешно загружены");
        } catch (Exception e) {
            model.addAttribute("message", "Ошибка в обработке файла: " + e.getMessage());
        }
        return "settings";
    }


}