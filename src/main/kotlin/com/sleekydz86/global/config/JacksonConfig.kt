package com.sleekydz86.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration // 또는 다른 적절한 패키지

@Configuration // 이 클래스가 Spring 설정 클래스임을 명시
class JacksonConfig {
    @Bean // 이 메서드가 반환하는 객체를 Spring 빈으로 등록
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
    }
}