package com.example.healthprofile.service;

import com.example.healthprofile.entity.User;
import com.example.healthprofile.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public void saveChatId(Long userId, Long chatId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setChatId(chatId);
            userRepository.save(user);
        }
    }

    public List<User> getAllUsersWithChatId() {
        return userRepository.findAllByChatIdIsNotNull();
    }
}
