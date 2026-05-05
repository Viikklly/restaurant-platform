package com.restaurant.auth_service.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User implements UserDetails {  /// UserDetails — интерфейс Spring Security

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING) /// Хранить в БД как текст ("USER", "ADMIN")
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist /// Вызывается ПЕРЕД сохранением НОВОЙ записи в БД
    /// Автоматически заполнять время создания и изменения
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate /// Вызывается ПЕРЕД обновлением СУЩЕСТВУЮЩЕЙ записи
    /// Автоматически заполнять время создания и изменения
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /// Spring Security методы
    /// Возвращает список ролей/прав пользователя (для проверки доступа)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getName()));
    }

    /// Проверяет, не истёк ли срок действия аккаунта
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /// Проверяет, не заблокирован ли аккаунт.
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /// Проверяет, активирован ли аккаунт
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
