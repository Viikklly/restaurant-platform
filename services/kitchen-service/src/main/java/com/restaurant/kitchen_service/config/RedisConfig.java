package com.restaurant.kitchen_service.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.hibernate.annotations.LazyCollectionOption;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Создаёт бин RedisTemplate для операций с Redis
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);           /// Создаёт новый шаблон и устанавливает фабрику подключений
        template.setKeySerializer(new StringRedisSerializer());     /// Устанавливает сериализатор для ключей
        template.setValueSerializer(jacksonSerializer());           /// Устанавливает сериализатор для значений
        template.setHashKeySerializer(new StringRedisSerializer()); /// Сериализатор для ключей хэшей
        template.setHashValueSerializer(jacksonSerializer());       /// Сериализатор для значений хэшей
        template.afterPropertiesSet();                              /// Выполняет проверку и инициализацию после установки всех свойств
        return template;
    }

    /**
     * Создаёт бин менеджера кэшей для Spring Cache abstraction
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))                    /// Устанавливает время жизни записей в кэше — 5 минут///
                .serializeKeysWith(                                 /// Для ключей кэша использует строковую сериализацию
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(                               /// Для значений кэша использует JSON-сериализацию через Jackson
                        RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer())
                )
                .disableCachingNullValues();                        /// Запрещает кэширование null значений

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Создаёт кастомный JSON-сериализатор
     */
    private GenericJackson2JsonRedisSerializer jacksonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        ///  Настройка времени
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        /// Настройка полиморфизма
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // Разрешаем пакеты Kitchen Service
                .allowIfSubType("com.restaurant.kitchen_service.entity")
                .allowIfSubType("com.restaurant.kitchen_service.dto")
                .allowIfSubType("com.restaurant.common.events")
                /// Разрешаем стандартные типы Java
                .allowIfBaseType(List.class)
                .allowIfBaseType(Map.class)
                .allowIfBaseType(BigDecimal.class)
                .allowIfBaseType(Integer.class)
                .allowIfBaseType(Long.class)
                .allowIfBaseType(String.class)
                .allowIfBaseType(Number.class)
                .build();

        objectMapper.activateDefaultTyping(
                ptv,                                    /// Валидатор типов
                ObjectMapper.DefaultTyping.NON_FINAL,   /// Для всех нефинальных классов
                JsonTypeInfo.As.PROPERTY                /// Как поле @class
        );

        /// Эта настройка контролирует поведение Jackson при десериализации JSON,
        /// когда встречаются неизвестные поля
        objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false  /// Игнорируем неизвестные поля
        );

        ///  Отладка
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}