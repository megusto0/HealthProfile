package com.example.healthprofile.service;

import com.example.healthprofile.entity.GarminData;
import com.example.healthprofile.entity.User;
import com.example.healthprofile.repository.GarminDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class GarminService {

    @Autowired
    private SimpMessagingTemplate template;

    @Autowired
    private GarminDataRepository garminDataRepository;
    private final UserService userService;

    public GarminService(UserService userService) {
        this.userService = userService;
    }

    public void updateGarminConfig(String startDate, long userId) throws IOException {
        User user = userService.getUserDetails();
        String login = user.getGarminLogin();
        String password = user.getGarminPassword();
        Path configPath = Paths.get("C:", "Users", "hecol", ".GarminDb", "GarminConnectConfig.json");
        ObjectMapper mapper = new ObjectMapper();

        // Считывание текущего JSON файла в ObjectNode
        ObjectNode rootNode = (ObjectNode) mapper.readTree(Files.newInputStream(configPath));

        // Обновление учетных данных
        rootNode.with("credentials")
                .put("user", login)
                .put("password", password)
                .put("secure_password", false);

        // Обновление директорий
        rootNode.with("directories")
                .put("base_dir", "/HealthData/HealthData" + userId);

        // Обновление дат
        rootNode.with("data")
                .put("weight_start_date", startDate)
                .put("sleep_start_date", startDate)
                .put("rhr_start_date", startDate)
                .put("monitoring_start_date", startDate)
                .put("download_latest_activities", 25)
                .put("download_all_activities", 1000);

        // Запись обновленного JSON обратно в файл
        Files.write(configPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rootNode));
    }

    public void runGarminDbCommand() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("python", "C:\\Users\\hecol\\miniconda3\\Scripts\\garmindb_cli.py", "--all", "--download", "--import", "--analyze");
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            template.convertAndSend("/topic/status", new Greeting(line));
            System.out.println(line);
        }
        int exitCode = process.waitFor();
        template.convertAndSend("/topic/status", new Greeting("Exited with error code : " + exitCode));
    }

    public void runGarminDbLatestCommand() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("python", "C:\\Users\\hecol\\miniconda3\\Scripts\\garmindb_cli.py", "--all", "--download", "--import", "--analyze", "--latest");
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            template.convertAndSend("/topic/status", new Greeting(line));
            System.out.println(line);
        }
        int exitCode = process.waitFor();
        template.convertAndSend("/topic/status", new Greeting("Exited with error code : " + exitCode));
    }

    public void transferHeartRateData(long userId) {
        String sqliteConnection = "jdbc:sqlite:C:/Users/hecol/HealthData/HealthData" + userId + "/DBs/garmin_monitoring.db";
        DateTimeFormatter formatterWithMillis = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
        DateTimeFormatter formatterWithoutMillis = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter formatterWithT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        int batchSize = 1000;

        String maxTimestampString = garminDataRepository.findMaxTimestampStringByUserId(userId);
        LocalDateTime maxTimestamp = null;
        if (maxTimestampString != null) {
            try {
                maxTimestamp = LocalDateTime.parse(maxTimestampString, formatterWithMillis);
            } catch (DateTimeParseException e) {
                try {
                    maxTimestamp = LocalDateTime.parse(maxTimestampString, formatterWithoutMillis);
                } catch (DateTimeParseException ex) {
                    maxTimestamp = LocalDateTime.parse(maxTimestampString, formatterWithT);
                }
            }
        }

        String query = "SELECT timestamp, heart_rate FROM monitoring_hr";
        if (maxTimestamp != null) {
            query += " WHERE timestamp > '" + maxTimestampString + "'";
        }

        try (Connection connection = DriverManager.getConnection(sqliteConnection);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {

            List<GarminData> garminDataList = new ArrayList<>();
            while (rs.next()) {
                String timestampStr = rs.getString("timestamp");
                LocalDateTime timestamp;
                try {
                    timestamp = LocalDateTime.parse(timestampStr, formatterWithMillis);
                } catch (DateTimeParseException e) {
                    try {
                        timestamp = LocalDateTime.parse(timestampStr, formatterWithoutMillis);
                    } catch (DateTimeParseException ex) {
                        timestamp = LocalDateTime.parse(timestampStr, formatterWithT);
                    }
                }

                GarminData garminData = new GarminData();
                garminData.setDate(timestamp.toLocalDate());
                garminData.setTime(timestamp.toLocalTime());
                garminData.setHeartRate(rs.getInt("heart_rate"));
                garminData.setUserId(userId);

                garminDataList.add(garminData);

                if (garminDataList.size() >= batchSize) {
                    garminDataRepository.saveAll(garminDataList);
                    garminDataList.clear();
                }
            }

            if (!garminDataList.isEmpty()) {
                garminDataRepository.saveAll(garminDataList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class Greeting {
        private String content;

        public Greeting() {}

        public Greeting(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}
