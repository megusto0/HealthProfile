package com.example.healthprofile.service;

import com.example.healthprofile.entity.HealthData;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

@Service
public class PdfReportService {

    public byte[] generateReport(Long userId, LocalDate startDate, LocalDate endDate, List<HealthData> healthData) throws DocumentException, IOException {
        List<HealthData> last7DaysData = healthData.stream()
                .filter(data -> data.getDate() != null && data.getDate().isAfter(endDate.minusDays(7)) && data.getDate().isBefore(endDate.plusDays(1)))
                .sorted(Comparator.comparing(HealthData::getDate).thenComparing(HealthData::getTime).reversed())
                .collect(Collectors.toList());

        List<HealthData> fastingData = filterFastingData(healthData);
        List<HealthData> postMealData = filterPostMealData(healthData);

        DoubleSummaryStatistics fastingStats = calculateStatistics(fastingData);
        DoubleSummaryStatistics postMealStats = calculateStatistics(postMealData);

        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);

        document.open();

        BaseFont baseFont = BaseFont.createFont("src/main/resources/fonts/tnr.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font font = new Font(baseFont, 12, Font.NORMAL);

        long daysWithEntries = healthData.stream()
                .map(HealthData::getDate)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        document.add(new Paragraph("Отчет за период: " + startDate + " - " + endDate + ". Данные за " + daysWithEntries + " дней.", font));

        addStatisticsTable(document, "Натощак", fastingStats, fastingData.size(), font);
        addStatisticsTable(document, "После еды", postMealStats, postMealData.size(), font);

        document.add(new Paragraph("Расходы за период", font));
        addExpendituresTable(document, healthData, startDate, endDate, font);

        document.add(new Paragraph("Типичный день за период", font));
        addTypicalDayTable(document, healthData, font);

        document.add(new Paragraph("Записи за последние 7 дней", font));
        addHealthDataTable(document, last7DaysData, font);

        document.close();

        return out.toByteArray();
    }

    private void addStatisticsTable(Document document, String title, DoubleSummaryStatistics stats, long count, Font font) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell(new Paragraph(title, font));
        cell.setColspan(2);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);

        table.addCell(new PdfPCell(new Paragraph("Средний уровень глюкозы", font)));
        table.addCell(new PdfPCell(new Paragraph(String.format("%.1f", stats.getAverage()), font)));

        table.addCell(new PdfPCell(new Paragraph("Максимальный уровень глюкозы", font)));
        table.addCell(new PdfPCell(new Paragraph(String.valueOf(stats.getMax()), font)));

        table.addCell(new PdfPCell(new Paragraph("Минимальный уровень глюкозы", font)));
        table.addCell(new PdfPCell(new Paragraph(String.valueOf(stats.getMin()), font)));

        table.addCell(new PdfPCell(new Paragraph("Количество записей", font)));
        table.addCell(new PdfPCell(new Paragraph(String.valueOf(count), font)));

        document.add(table);
    }

    private void addHealthDataTable(Document document, List<HealthData> data, Font font) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);

        table.addCell(new PdfPCell(new Paragraph("Дата", font)));
        table.addCell(new PdfPCell(new Paragraph("Время", font)));
        table.addCell(new PdfPCell(new Paragraph("Уровень глюкозы", font)));
        table.addCell(new PdfPCell(new Paragraph("Доза инсулина", font)));
        table.addCell(new PdfPCell(new Paragraph("Прием пищи", font)));
        table.addCell(new PdfPCell(new Paragraph("Углеводы", font)));

        for (HealthData entry : data) {
            table.addCell(new PdfPCell(new Paragraph(entry.getDate() != null ? entry.getDate().toString() : "", font)));
            table.addCell(new PdfPCell(new Paragraph(entry.getTime() != null ? entry.getTime().toString() : "", font)));
            table.addCell(new PdfPCell(new Paragraph(entry.getGlucoseLevel() != null ? entry.getGlucoseLevel().toString() : "", font)));
            table.addCell(new PdfPCell(new Paragraph(entry.getInsulinDose() != null ? entry.getInsulinDose().toString() : "", font)));
            table.addCell(new PdfPCell(new Paragraph(entry.getFoodIntake() != null ? entry.getFoodIntake() : "", font)));
            table.addCell(new PdfPCell(new Paragraph(entry.getCarbohydrates() != null ? entry.getCarbohydrates().toString() : "", font)));
        }

        document.add(table);
    }

    private void addTypicalDayTable(Document document, List<HealthData> healthData, Font font) throws DocumentException {
        Map<String, Map<LocalDate, List<HealthData>>> groupedData = healthData.stream()
                .collect(Collectors.groupingBy(
                        data -> {
                            int hour = data.getTime() != null ? data.getTime().getHour() : 0;
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
                        },
                        Collectors.groupingBy(HealthData::getDate)
                ));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);

        table.addCell(new PdfPCell(new Paragraph("Время", font)));
        table.addCell(new PdfPCell(new Paragraph("Уровень глюкозы", font)));
        table.addCell(new PdfPCell(new Paragraph("Доза инсулина", font)));
        table.addCell(new PdfPCell(new Paragraph("Углеводы", font)));

        String[] timesOfDay = {"00:00 - 06:00", "06:00 - 10:00", "10:00 - 14:00", "14:00 - 18:00", "18:00 - 22:00", "22:00 - 00:00"};
        for (String timeOfDay : timesOfDay) {
            Map<LocalDate, List<HealthData>> dailySummaries = groupedData.getOrDefault(timeOfDay, Collections.emptyMap());

            List<Double> glucoseLevels = dailySummaries.values().stream()
                    .flatMap(List::stream)
                    .map(HealthData::getGlucoseLevel)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<Double> insulinSums = dailySummaries.values().stream()
                    .map(entries -> entries.stream()
                            .map(HealthData::getInsulinDose)
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .sum())
                    .collect(Collectors.toList());

            List<Double> carbSums = dailySummaries.values().stream()
                    .map(entries -> entries.stream()
                            .map(HealthData::getCarbohydrates)
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .sum())
                    .collect(Collectors.toList());

            double medianGlucose = calculateMedian(glucoseLevels);
            double medianInsulin = calculateMedian(insulinSums);
            double medianCarbs = calculateMedian(carbSums);

            table.addCell(new PdfPCell(new Paragraph(timeOfDay, font)));
            table.addCell(new PdfPCell(new Paragraph(String.format("%.1f", medianGlucose), font)));
            table.addCell(new PdfPCell(new Paragraph(String.format("%.1f", medianInsulin), font)));
            table.addCell(new PdfPCell(new Paragraph(String.format("%.1f", medianCarbs), font)));
        }

        document.add(table);
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

    private void addExpendituresTable(Document document, List<HealthData> healthData, LocalDate startDate, LocalDate endDate, Font font) throws DocumentException {
        List<HealthData> filteredData = healthData.stream()
                .filter(data -> data.getDate() != null && data.getDate().isAfter(startDate.minusDays(1)) && data.getDate().isBefore(endDate.plusDays(1)))
                .toList();

        long glucoseMeasurementsCount = filteredData.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .count();

        long catheterChangesCount = filteredData.stream()
                .filter(data -> data.getCatheterChange() != null && data.getCatheterChange())
                .count();

        long ampouleChangesCount = filteredData.stream()
                .filter(data -> data.getAmpouleChange() != null && data.getAmpouleChange())
                .count();

        Map<LocalDate, DoubleSummaryStatistics> insulinStatsPerDay = filteredData.stream()
                .filter(data -> data.getInsulinDose() != null)
                .collect(Collectors.groupingBy(
                        HealthData::getDate,
                        Collectors.summarizingDouble(HealthData::getInsulinDose)
                ));

        double averageDailyInsulin = insulinStatsPerDay.values().stream()
                .mapToDouble(DoubleSummaryStatistics::getSum)
                .average()
                .orElse(0.0);

        long daysWithGlucoseMeasurements = filteredData.stream()
                .filter(data -> data.getGlucoseLevel() != null)
                .map(HealthData::getDate)
                .distinct()
                .count();

        double averageGlucoseMeasurementsPerDay = daysWithGlucoseMeasurements == 0 ? 0 : (double) glucoseMeasurementsCount / daysWithGlucoseMeasurements;

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell(new Paragraph("Расходы за период", font));
        cell.setColspan(2);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);

        table.addCell(new PdfPCell(new Paragraph("Количество измерений глюкозы", font)));
        table.addCell(new PdfPCell(new Paragraph(String.valueOf(glucoseMeasurementsCount), font)));

        table.addCell(new PdfPCell(new Paragraph("Количество смен катетера", font)));
        table.addCell(new PdfPCell(new Paragraph(String.valueOf(catheterChangesCount), font)));

        table.addCell(new PdfPCell(new Paragraph("Количество смен ампулы", font)));
        table.addCell(new PdfPCell(new Paragraph(String.valueOf(ampouleChangesCount), font)));

        table.addCell(new PdfPCell(new Paragraph("Средняя доза инсулина за день", font)));
        table.addCell(new PdfPCell(new Paragraph(String.format("%.1f", averageDailyInsulin), font)));

        table.addCell(new PdfPCell(new Paragraph("Среднее количество измерений глюкозы в день", font)));
        table.addCell(new PdfPCell(new Paragraph(String.format("%.1f", averageGlucoseMeasurementsPerDay), font)));

        document.add(table);
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
}
