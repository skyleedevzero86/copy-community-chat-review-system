package com.sleekydz86.global.jobhandler

import com.sleekydz86.domain.comment.service.CommentSyncService
import com.xxl.job.core.context.XxlJobHelper
import com.xxl.job.core.handler.annotation.XxlJob
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CommentSyncXxlJobHandler {

    companion object {
        private val log = LoggerFactory.getLogger(CommentSyncXxlJobHandler::class.java) // 로거
    }

    @Autowired
    private lateinit var commentSyncService: CommentSyncService // 댓글 동기화 서비스

    @XxlJob("syncHotCommentsJobHandler")
    fun executeSyncHotComments() {
        XxlJobHelper.log("【인기 댓글 동기화 작업】 시작...")
        log.info("【인기 댓글 동기화 작업】 XXL-Job 트리거, 실행 시작...")

        try {
            // 핵심 비즈니스 로직 호출
            commentSyncService.syncHotCommentsToRedis()

            XxlJobHelper.log("【인기 댓글 동기화 작업】 실행 성공!")
            log.info("【인기 댓글 동기화 작업】 실행 성공!")

            // 실행 성공 보고
            XxlJobHelper.handleSuccess()
        } catch (e: Exception) {
            XxlJobHelper.log("【인기 댓글 동기화 작업】 실행 실패! 오류 메시지: {}", e.message)
            log.error("【인기 댓글 동기화 작업】 실행 실패!", e)

            // 실행 실패 보고
            XxlJobHelper.handleFail(e.message)
        }
    }
}