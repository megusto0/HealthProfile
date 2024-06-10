package com.example.healthprofile.controller;

import com.example.healthprofile.entity.HealthData;
import com.example.healthprofile.service.HealthDataService;
import com.example.healthprofile.service.PdfReportService;
import com.example.healthprofile.service.UserService;
import com.itextpdf.text.DocumentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ReportController {

    private final HealthDataService healthDataService;
    private final PdfReportService pdfReportService;
    private final UserService userService;

    @Autowired
    public ReportController(HealthDataService healthDataService, PdfReportService pdfReportService, UserService userService, UserService userService1) {
        this.healthDataService = healthDataService;
        this.pdfReportService = pdfReportService;
        this.userService = userService1;
    }

    @GetMapping("/report")
    public ResponseEntity<byte[]> generateReport(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws DocumentException, IOException {

        Long userId = userService.getUserDetails().getId();
        List<HealthData> healthData = healthDataService.getHealthDataForCurrentUserInRange(userId, startDate, endDate);

        byte[] pdfBytes = pdfReportService.generateReport(userId, startDate, endDate, healthData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping("/reports")
    public String showDefaultReport(Model model) throws DocumentException, IOException {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        long userId = userService.getUserDetails().getId();
        List<HealthData> healthData = getHealthDataForUser(userId, startDate, endDate);

        DoubleSummaryStatistics fastingStats = calculateStatistics(filterFastingData(healthData));
        DoubleSummaryStatistics postMealStats = calculateStatistics(filterPostMealData(healthData));

        long glucoseMeasurementsCount = countNonNull(healthData, HealthData::getGlucoseLevel);
        long catheterChangesCount = countTrue(healthData, HealthData::getCatheterChange);
        long ampouleChangesCount = countTrue(healthData, HealthData::getAmpouleChange);
        Map<LocalDate, DoubleSummaryStatistics> insulinStatsPerDay = groupStatisticsByDate(healthData, HealthData::getInsulinDose);
        double averageDailyInsulin = calculateAverage(insulinStatsPerDay, DoubleSummaryStatistics::getSum);
        long daysWithGlucoseMeasurements = countDistinct(healthData, HealthData::getGlucoseLevel, HealthData::getDate);
        double averageGlucoseMeasurementsPerDay = calculatePerDayAverage(glucoseMeasurementsCount, daysWithGlucoseMeasurements);

        List<Map<String, Object>> typicalDayData = getTypicalDayData(healthData);

        populateModelForShow(model, startDate, endDate, fastingStats, postMealStats, glucoseMeasurementsCount, catheterChangesCount, ampouleChangesCount, averageDailyInsulin, averageGlucoseMeasurementsPerDay, typicalDayData);

        return "reports";
    }

    private List<HealthData> getHealthDataForUser(long userId, LocalDate startDate, LocalDate endDate) {
        return healthDataService.getHealthDataForCurrentUserInRange(userId, startDate, endDate);
    }

    private void populateModelForShow(Model model, LocalDate startDate, LocalDate endDate, DoubleSummaryStatistics fastingStats, DoubleSummaryStatistics postMealStats, long glucoseMeasurementsCount, long catheterChangesCount, long ampouleChangesCount, double averageDailyInsulin, double averageGlucoseMeasurementsPerDay, List<Map<String, Object>> typicalDayData) {
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("fastingAverage", fastingStats.getAverage());
        model.addAttribute("fastingMax", fastingStats.getMax());
        model.addAttribute("fastingMin", fastingStats.getMin());
        model.addAttribute("fastingCount", fastingStats.getCount());
        model.addAttribute("postMealAverage", postMealStats.getAverage());
        model.addAttribute("postMealMax", postMealStats.getMax());
        model.addAttribute("postMealMin", postMealStats.getMin());
        model.addAttribute("postMealCount", postMealStats.getCount());
        model.addAttribute("glucoseMeasurementsCount", glucoseMeasurementsCount);
        model.addAttribute("catheterChangesCount", catheterChangesCount);
        model.addAttribute("ampouleChangesCount", ampouleChangesCount);
        model.addAttribute("averageDailyInsulin", averageDailyInsulin);
        model.addAttribute("averageGlucoseMeasurementsPerDay", averageGlucoseMeasurementsPerDay);
        model.addAttribute("typicalDayData", typicalDayData);
    }

    private long countNonNull(List<HealthData> healthData, java.util.function.Function<HealthData, Object> mapper) {
        return healthData.stream().filter(data -> mapper.apply(data) != null).count();
    }

    private long countTrue(List<HealthData> healthData, java.util.function.Function<HealthData, Boolean> mapper) {
        return healthData.stream().filter(data -> Boolean.TRUE.equals(mapper.apply(data))).count();
    }

    private long countDistinct(List<HealthData> healthData, java.util.function.Function<HealthData, Object> filterMapper, java.util.function.Function<HealthData, LocalDate> dateMapper) {
        return healthData.stream().filter(data -> filterMapper.apply(data) != null).map(dateMapper).distinct().count();
    }

    private Map<LocalDate, DoubleSummaryStatistics> groupStatisticsByDate(List<HealthData> healthData, java.util.function.Function<HealthData, Double> mapper) {
        return healthData.stream()
                .filter(data -> mapper.apply(data) != null)
                .collect(Collectors.groupingBy(HealthData::getDate, Collectors.summarizingDouble(mapper::apply)));
    }

    private double calculateAverage(Map<LocalDate, DoubleSummaryStatistics> statsMap, java.util.function.ToDoubleFunction<DoubleSummaryStatistics> mapper) {
        return statsMap.values().stream().mapToDouble(mapper).average().orElse(0.0);
    }

    private double calculatePerDayAverage(long total, long distinctDays) {
        return distinctDays == 0 ? 0 : (double) total / distinctDays;
    }

    private List<Map<String, Object>> getTypicalDayData(List<HealthData> healthData) {
        Map<String, Map<LocalDate, List<HealthData>>> groupedData = groupDataByTimeOfDay(healthData);

        String[] timesOfDay = {"00:00 - 06:00", "06:00 - 10:00", "10:00 - 14:00", "14:00 - 18:00", "18:00 - 22:00", "22:00 - 00:00"};
        List<Map<String, Object>> typicalDayData = new ArrayList<>();

        for (String timeOfDay : timesOfDay) {
            Map<LocalDate, List<HealthData>> dailySummaries = groupedData.getOrDefault(timeOfDay, Collections.emptyMap());

            double medianGlucose = calculateMedian(getValues(dailySummaries, HealthData::getGlucoseLevel));
            double medianInsulin = calculateMedian(sumValues(dailySummaries, HealthData::getInsulinDose));
            double medianCarbs = calculateMedian(sumValues(dailySummaries, HealthData::getCarbohydrates));

            typicalDayData.add(createTimeSlotData(timeOfDay, medianGlucose, medianInsulin, medianCarbs));
        }

        return typicalDayData;
    }

    private Map<String, Map<LocalDate, List<HealthData>>> groupDataByTimeOfDay(List<HealthData> healthData) {
        return healthData.stream()
                .collect(Collectors.groupingBy(
                        data -> determineTimeSlot(data.getTime() != null ? data.getTime().getHour() : 0),
                        Collectors.groupingBy(HealthData::getDate)
                ));
    }

    private String determineTimeSlot(int hour) {
        if (hour < 6) {
            return "00:00 - 06:00";
        } else if (hour < 10) {
            return "06:00 - 10:00";
        } else if (hour < 14) {
            return "10:00 - 14:00";
        } else if (hour < 18) {
            return "14:00 - 18:00";
        } else if (hour < 22) {
            return "18:00 - 22:00";
        } else {
            return "22:00 - 00:00";
        }
    }

    private List<Double> getValues(Map<LocalDate, List<HealthData>> dailySummaries, java.util.function.Function<HealthData, Double> mapper) {
        return dailySummaries.values().stream()
                .flatMap(List::stream)
                .map(mapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Double> sumValues(Map<LocalDate, List<HealthData>> dailySummaries, java.util.function.Function<HealthData, Double> mapper) {
        return dailySummaries.values().stream()
                .map(entries -> entries.stream()
                        .map(mapper)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .sum())
                .collect(Collectors.toList());
    }

    private Map<String, Object> createTimeSlotData(String timeOfDay, double medianGlucose, double medianInsulin, double medianCarbs) {
        Map<String, Object> timeSlotData = new HashMap<>();
        timeSlotData.put("timeOfDay", timeOfDay);
        timeSlotData.put("medianGlucose", medianGlucose);
        timeSlotData.put("medianInsulin", medianInsulin);
        timeSlotData.put("medianCarbs", medianCarbs);
        return timeSlotData;
    }

    public List<HealthData> filterFastingData(List<HealthData> healthData) {
        return healthData.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .filter(data -> isFasting(data, healthData))
                .collect(Collectors.toList());
    }

    public List<HealthData> filterPostMealData(List<HealthData> healthData) {
        return healthData.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .filter(data -> !isFasting(data, healthData))
                .collect(Collectors.toList());
    }

    private boolean isFasting(HealthData data, List<HealthData> allData) {
        LocalDateTime measurementTime = LocalDateTime.of(data.getDate(), data.getTime());
        LocalDateTime sixHoursBefore = measurementTime.minusHours(6);

        return allData.stream()
                .filter(d -> d.getCarbohydrates() != null)
                .map(d -> LocalDateTime.of(d.getDate(), d.getTime()))
                .noneMatch(time -> time.isAfter(sixHoursBefore) && time.isBefore(measurementTime));
    }

    public DoubleSummaryStatistics calculateStatistics(List<HealthData> healthData) {
        return healthData.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .mapToDouble(HealthData::getGlucoseLevel)
                .summaryStatistics();
    }

    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        Collections.sort(values);
        int middle = values.size() / 2;
        if (values.size() % 2 == 0) {
            return (values.get(middle - 1) + values.get(middle)) / 2.0;
        } else {
            return values.get(middle);
        }
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Model model, Exception e) {
        model.addAttribute("error", "An unexpected error occurred: " + e.getMessage());
        return "error";
    }
}