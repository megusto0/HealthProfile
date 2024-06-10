package com.example.healthprofile.security;

import com.example.healthprofile.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Настройка правил доступа
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/register", "/login").permitAll()  // Разрешаем доступ к формам регистрации и входа
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()  // Разрешаем доступ к статическим ресурсам
                        .anyRequest().authenticated()  // Требуем аутентификацию для всех остальных запросов
                )
                .formLogin(form -> form
                        .loginPage("/login")  // Указываем страницу для входа
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)  // Перенаправление после успешного входа
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")  // URL для инициации выхода
                        .logoutSuccessUrl("/login")  // Перенаправление после выхода
                        .permitAll()
                );
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();  // Использование BCrypt для хеширования паролей
    }
}
