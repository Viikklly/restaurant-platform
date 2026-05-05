package com.restaurant.auth_service.service;


import com.restaurant.auth_service.entity.User;
import com.restaurant.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


/**
 * Интерфейс Spring Security, который загружает пользователя из базы данных по email/username
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;


    /// username — это email пользователя
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        log.debug("Attempting to load user by username/email: {}", username);

        /// Ищем пользователя по email
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", username);
                    return new UsernameNotFoundException("User not found with email: " + username);
                });

        log.debug("User found: {}, role: {}", user.getEmail(), user.getRole());

        /// User уже implements UserDetails, так что возвращаем его как есть
        return user;
    }
}
