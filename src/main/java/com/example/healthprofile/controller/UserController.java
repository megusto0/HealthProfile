package com.example.healthprofile.controller;

import com.example.healthprofile.entity.UserDto;
import com.example.healthprofile.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new UserDto());
        return "register";
    }


    @PostMapping("/register")
    public String registerUserAccount(@ModelAttribute("user") UserDto userDto, BindingResult result) {
        if (result.hasErrors()) {
            return "register";
        }
        userService.registerNewUserAccount(userDto);
        return "redirect:/login";
    }
}

