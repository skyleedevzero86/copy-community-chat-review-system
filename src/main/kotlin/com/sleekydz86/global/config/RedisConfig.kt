package com.sleekydz86.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)

            // 키의 직렬화 방식 설정
            keySerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()

            // 값의 직렬화 방식 설정
            valueSerializer = GenericJackson2JsonRedisSerializer()
            hashValueSerializer = GenericJackson2JsonRedisSerializer()

            afterPropertiesSet()
        }
    }
}