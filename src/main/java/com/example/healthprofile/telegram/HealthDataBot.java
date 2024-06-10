package com.example.healthprofile.telegram;

import com.example.healthprofile.entity.HealthData;
import com.example.healthprofile.entity.User;
import com.example.healthprofile.service.AuthService;
import com.example.healthprofile.service.HealthDataService;
import com.example.healthprofile.service.PdfReportService;
import com.example.healthprofile.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HealthDataBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final AuthService authService;
    private final UserService userService;
    private final HealthDataService healthDataService;
    private final PdfReportService pdfReportService;

    private final Map<Long, Boolean> authenticatedUsers = new HashMap<>();
    private final Map<Long, Long> userIds = new HashMap<>();
    private static final Pattern REPORT_DATE_PATTERN = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");
    private static final Pattern GLUCOSE_PATTERN = Pattern.compile("сахар\\s+(\\d+(\\.\\d+)?)");
    private static final Pattern INSULIN_PATTERN = Pattern.compile("инсулин\\s+(\\d+(\\.\\d+)?)");
    private static final Pattern CARBS_PATTERN = Pattern.compile("углеводы\\s+(\\d+(\\.\\d+)?)");
    private static final Pattern FOOD_PATTERN = Pattern.compile("еда\\s+(\\s+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("дата\\s+(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern TIME_PATTERN = Pattern.compile("время\\s+(\\d{1,2}:\\d{2})");

    // Создаем DateTimeFormatter для парсинга времени в форматах "H:mm" и "HH:mm"
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter();

    @Autowired
    public HealthDataBot(@Value("${telegram.bot.token}") String botToken,
                         @Value("${telegram.bot.username}") String botUsername,
                         AuthService authService,
                         UserService userService,
                         HealthDataService healthDataService,
                         PdfReportService pdfReportService) {
        super(botToken);
        this.botUsername = botUsername;
        this.authService = authService;
        this.userService = userService;
        this.healthDataService = healthDataService;
        this.pdfReportService = pdfReportService;
    }

    @PostConstruct
    public void init() {
        List<User> users = authService.getAllUsersWithChatId();
        for (User user : users) {
            authenticatedUsers.put(user.getChatId(), true);
            userIds.put(user.getChatId(), user.getId());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            if (messageText.startsWith("/login")) {
                handleLogin(chatId, messageText);
            } else if (messageText.startsWith("/logout")) {
                handleLogout(chatId);
            } else if (messageText.startsWith("/myid")) {
                handleMyId(chatId);
            } else if (messageText.toLowerCase().startsWith("/add")) {
                handleAddHealthData(chatId, messageText.substring(5));  // Отрезаем '/add '
            } else if (messageText.toLowerCase().startsWith("/д") || messageText.toLowerCase().startsWith("/доб")) {
                handleAddHealthData(chatId, messageText.substring(3));  // Отрезаем '/д' или '/доб'
            } else if (messageText.startsWith("/отчет")) {
                handleReport(chatId, messageText);
            }
        }
    }

    private void handleLogin(long chatId, String messageText) {
        String[] parts = messageText.split(" ");
        if (parts.length < 3) {
            sendTextMessage(chatId, "Чтобы войти используйте команду: /login <username> <password>");
            return;
        }
        String username = parts[1];
        String password = parts[2];

        User user = authService.authenticate(username, password);
        if (user != null) {
            authenticatedUsers.put(chatId, true);
            userIds.put(chatId, user.getId());
            authService.saveChatId(user.getId(), chatId);
            sendTextMessage(chatId, "Вы успешно вошли");
        } else {
            sendTextMessage(chatId, "Неверное имя пользователя или пароль");
        }
    }

    private void handleLogout(long chatId) {
        Long userId = userIds.get(chatId);
        if (userId != null) {
            User user = userService.findById(userId);
            if (user != null) {
                user.setChatId(null);
                userService.save(user);
            }
        }
        authenticatedUsers.remove(chatId);
        userIds.remove(chatId);
        sendTextMessage(chatId, "Вы успешно вышли из аккаунта.");
    }

    private void handleMyId(long chatId) {
        if (isAuthenticated(chatId)) {
            Long userId = userIds.get(chatId);
            sendTextMessage(chatId, "Ваш ID: " + userId);
        } else {
            sendTextMessage(chatId, "Войдите, чтобы посмотреть ID.");
        }
    }

    private boolean isAuthenticated(long chatId) {
        return authenticatedUsers.getOrDefault(chatId, false);
    }

    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void handleAddHealthData(long chatId, String messageText) {
        if (!isAuthenticated(chatId)) {
            sendTextMessage(chatId, "Войдите, чтобы добавить запись.");
            return;
        }

        HealthData healthData = new HealthData();
        healthData.setUserId(userIds.get(chatId));
        healthData.setDate(LocalDate.now());
        healthData.setTime(LocalTime.now().withSecond(0).withNano(0));

        Matcher glucoseMatcher = GLUCOSE_PATTERN.matcher(messageText);
        if (glucoseMatcher.find()) {
            healthData.setGlucoseLevel(Double.parseDouble(glucoseMatcher.group(1)));
        }

        Matcher insulinMatcher = INSULIN_PATTERN.matcher(messageText);
        if (insulinMatcher.find()) {
            healthData.setInsulinDose(Double.parseDouble(insulinMatcher.group(1)));
        }

        Matcher carbsMatcher = CARBS_PATTERN.matcher(messageText);
        if (carbsMatcher.find()) {
            healthData.setCarbohydrates(Double.parseDouble(carbsMatcher.group(1)));
        }

        Matcher foodMatcher = FOOD_PATTERN.matcher(messageText);
        if (foodMatcher.find()) {
            healthData.setFoodIntake(foodMatcher.group(1));
        }

        Matcher dateMatcher = DATE_PATTERN.matcher(messageText);
        if (dateMatcher.find()) {
            healthData.setDate(LocalDate.parse(dateMatcher.group(1), DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        }

        Matcher timeMatcher = TIME_PATTERN.matcher(messageText);
        if (timeMatcher.find()) {
            healthData.setTime(LocalTime.parse(timeMatcher.group(1), TIME_FORMATTER));
        }

        healthDataService.save(healthData);
        sendTextMessage(chatId, "Запись успешно добавлена.");
    }

    private void handleReport(long chatId, String messageText) {
        if (!isAuthenticated(chatId)) {
            sendTextMessage(chatId, "Войдите, чтобы запросить отчет.");
            return;
        }

        String[] parts = messageText.split(" ");
        if (parts.length != 3) {
            sendTextMessage(chatId, "Используйте команду в формате: /отчет дд.мм.гггг дд.мм.гггг");
            return;
        }

        String startDateStr = parts[1];
        String endDateStr = parts[2];

        Matcher startMatcher = REPORT_DATE_PATTERN.matcher(startDateStr);
        Matcher endMatcher = REPORT_DATE_PATTERN.matcher(endDateStr);

        if (!startMatcher.matches() || !endMatcher.matches()) {
            sendTextMessage(chatId, "Неверный формат даты. Используйте дд.мм.гггг");
            return;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate startDate = LocalDate.parse(startDateStr, formatter);
            LocalDate endDate = LocalDate.parse(endDateStr, formatter);

            Long userId = userIds.get(chatId);
            List<HealthData> healthData = healthDataService.getHealthDataForCurrentUserInRange(userId, startDate, endDate);
            byte[] pdfBytes = pdfReportService.generateReport(userId, startDate, endDate, healthData);

            sendDocument(chatId, pdfBytes, "report.pdf");
        } catch (Exception e) {
            sendTextMessage(chatId, "Произошла ошибка при создании отчета. Пожалуйста, попробуйте еще раз.");
            e.printStackTrace();
        }
    }

    private void sendDocument(long chatId, byte[] pdfBytes, String filename) {
        SendDocument sendDocumentRequest = new SendDocument();
        sendDocumentRequest.setChatId(String.valueOf(chatId));
        sendDocumentRequest.setDocument(new InputFile(new ByteArrayInputStream(pdfBytes), filename));
        try {
            execute(sendDocumentRequest);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendNotification(Long userId, String message) {
        User user = userService.findById(userId);
        if (user != null && user.getChatId() != null) {
            sendTextMessage(user.getChatId(), message);
        }
    }
}