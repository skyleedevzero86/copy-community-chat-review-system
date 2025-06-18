package com.sleekydz86.global.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

    @Bean
    fun redisTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper // ObjectMapper 주입
    ): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)

            // Jackson 직렬화기 설정
            val serializer = GenericJackson2JsonRedisSerializer(objectMapper)

            // 키 직렬화
            keySerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()

            // 값 직렬화
            valueSerializer = serializer
            hashValueSerializer = serializer

            afterPropertiesSet()
        }
    }
}