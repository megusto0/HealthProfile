package com.example.healthprofile.controller;

import com.example.healthprofile.entity.User;
import com.example.healthprofile.service.GarminService;
import com.example.healthprofile.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import java.io.IOException;

@Controller
public class GarminConnectController {

    private final GarminService garminService;
    private final UserService userService;


    @Autowired
    public GarminConnectController(GarminService garminService, UserService userService, UserService userService1) {
        this.garminService = garminService;
        this.userService = userService1;
    }

    @PostMapping("/submitGarminData")
    public String submitGarminData(@RequestParam String login, @RequestParam String password, @RequestParam String startDate, Model model) throws IOException, InterruptedException {
        User user = userService.getUserDetails();
        user.setGarminLogin(login);
        user.setGarminPassword(password);
        long userId = user.getId();
        garminService.updateGarminConfig(startDate, userId);
        garminService.runGarminDbCommand();
        garminService.transferHeartRateData(userId);

        model.addAttribute("loading", true);
        return "redirect:/settings";
    }

    @PostMapping("/updateGarminData")
    public String updateGarminData() {
        try {
            garminService.runGarminDbLatestCommand();
            User user = userService.getUserDetails();
            long userId = user.getId();
            garminService.transferHeartRateData(userId);
            return "redirect:/";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "error";
        }
    }
}

