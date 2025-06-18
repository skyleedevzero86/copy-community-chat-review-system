package com.sleekydz86.global.config

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class XxlJobConfig {

    @Value("\${xxl.job.admin.addresses}")
    private lateinit var adminAddresses: String // 관리자 주소

    @Value("\${xxl.job.accessToken}")
    private lateinit var accessToken: String // 액세스 토큰

    @Value("\${xxl.job.executor.appname}")
    private lateinit var appname: String // 애플리케이션 이름

    @Value("\${xxl.job.executor.address}")
    private lateinit var address: String // 실행기 주소

    @Value("\${xxl.job.executor.ip}")
    private lateinit var ip: String // 실행기 IP

    @Value("\${xxl.job.executor.port}")
    private var port: Int = 0 // 실행기 포트

    @Value("\${xxl.job.executor.logpath}")
    private lateinit var logPath: String // 로그 경로

    @Value("\${xxl.job.executor.logretentiondays}")
    private var logRetentionDays: Int = 0 // 로그 보존 기간

    @Bean
    fun xxlJobExecutor(): XxlJobSpringExecutor {
        return XxlJobSpringExecutor().apply {
            adminAddresses = this@XxlJobConfig.adminAddresses // 관리자 주소 설정
            appname = this@XxlJobConfig.appname // 애플리케이션 이름 설정
            address = this@XxlJobConfig.address // 실행기 주소 설정
            ip = this@XxlJobConfig.ip // 실행기 IP 설정
            port = this@XxlJobConfig.port // 실행기 포트 설정
            accessToken = this@XxlJobConfig.accessToken // 액세스 토큰 설정
            logPath = this@XxlJobConfig.logPath // 로그 경로 설정
            logRetentionDays = this@XxlJobConfig.logRetentionDays // 로그 보존 기간 설정
        }
    }
}