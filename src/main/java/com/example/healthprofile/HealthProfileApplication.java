package com.example.healthprofile;

import com.example.healthprofile.telegram.HealthDataBot;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@SpringBootApplication
public class HealthProfileApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthProfileApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(HealthDataBot bot) {
        return args -> {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
        };
    }

}
