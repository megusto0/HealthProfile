package com.example.healthprofile.repository;

import com.example.healthprofile.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
    List<User> findAllByChatIdIsNotNull();
}
