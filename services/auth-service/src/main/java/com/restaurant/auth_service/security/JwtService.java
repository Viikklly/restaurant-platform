package com.restaurant.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/// JwtService — это сервис, который:
/// Генерирует JWT-токен при успешном логине
/// Валидирует токен (проверяет, не подделан ли он и не истёк ли)
/// Извлекает данные из токена (например, email пользователя)

@Service
public class JwtService {

    @Value("${jwt.secret}")  /// Читает значение из application.yml или bootstrap.yml
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /// Получение секретного ключа
    /// Превращает секретную строку в ключ для подписи
    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();  /// Превращает строку в массив байтов
        return Keys.hmacShaKeyFor(keyBytes); /// Создаёт ключ для HMAC-SHA256 подписи
    }

    /// Извлекает из токена subject (мы туда положим email пользователя)
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /// Извлекает поле из токена
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /// Парсит токен: проверяет подпись, извлекает все данные (payload)
    /// Если подпись неверна или токен просрочен — выбросит исключение
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /// Проверка валидности токена
    /// Email в токене совпадает с email пользователя и Токен не истёк
    public Boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /// Сравнивает exp (время истечения) с текущим моментом
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /// Достаёт email из токена
    /// Достаёт из токена поле expiration
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /// Генерация токенов

    /// Создаёт новый JWT при логине.
    /// Создаёт access-токен при логине, при регистрации
    /// 15 минут
    /// хранится в памяти/переменной
    /// содержит email, роль, userId
    /// Частота отправки - каждым запросом
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /// Создаёт refresh-токен (живёт дольше)
    /// Чтобы обновлять access-токен без повторного ввода пароля.
    /// 7 дней
    /// хранится HttpOnly Cookie / Secure Storage
    /// содержит идентификатор + тип "refresh"
    /// Частота отправки только при обновлении
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userDetails, refreshExpiration);
    }

    /// Создаёт новый JWT при логине
    /// Создаёт токен с дополнительными данными.
    /// Если нужно добавить role, userId
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, expiration);
    }


    /// Строитель токена
    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, Long expirationTime) {
        return Jwts.builder()
                .setClaims(extraClaims)                                                 /// Добавляет кастомные поля
                .setSubject(userDetails.getUsername())                                  /// Главное поле — идентификатор пользователя
                .setIssuedAt(new Date(System.currentTimeMillis()))                      /// Время создания токена
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))   /// Время истечения
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)                    ///  Подписывает токен секретным ключом HS256
                .compact();                                                             /// Собирает всё в финальную строку
    }


    /// Для GATEWAY

    /// Извлекает userId (subject) из токена
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /// Извлекает email из токена
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /// Извлекает role из токена
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /// Проверяет валидность токена (без UserDetails) - для Gateway
    public Boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
