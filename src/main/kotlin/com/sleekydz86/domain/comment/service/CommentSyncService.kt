package com.sleekydz86.domain.comment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sleekydz86.domain.comment.repository.CommentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Service
class CommentSyncService {

    companion object {
        private val log = LoggerFactory.getLogger(CommentSyncService::class.java) // 로거
        private const val KEY_HOT_COMMENTS = "hot_comments" // 인기 댓글 키
        private const val KEY_HOT_COMMENTS_TEMP = "hot_comments_temp" // 임시 인기 댓글 키
        private const val KEY_COMMENT_CACHE_PREFIX = "comment:" // 댓글 캐시 키 접두사
    }

    @Autowired
    private lateinit var commentRepository: CommentRepository // 댓글 리포지토리

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any> // Redis 템플릿

    @Autowired
    private lateinit var objectMapper: ObjectMapper // JSON 매핑 객체

    @Transactional(readOnly = true)
    fun syncHotCommentsToRedis() {
        log.info("인기 댓글 데이터 동기화 비즈니스 로직 시작...")

        // 1. 동기화 범위 정의
        val sevenDaysAgo = LocalDateTime.now().minusDays(7)
        val pageRequest = PageRequest.of(0, 1000) // 상위 1000개

        // 2. TiDB에서 조회
        val hotComments = commentRepository.findTopLikedCommentsSince(sevenDaysAgo, pageRequest)
        if (hotComments.isEmpty()) {
            log.info("동기화할 인기 댓글이 없음, 작업 종료.")
            return
        }
        log.info("데이터베이스에서 {}개의 인기 댓글 조회 완료.", hotComments.size)

        // 3. 임시 키에 데이터 작성
        redisTemplate.delete(KEY_HOT_COMMENTS_TEMP)
        hotComments.forEach { comment ->
            redisTemplate.opsForZSet().add(KEY_HOT_COMMENTS_TEMP, comment.id.toString(), comment.likes.toDouble())
            val cacheKey = "$KEY_COMMENT_CACHE_PREFIX${comment.id}"
            val commentJson = objectMapper.writeValueAsString(comment)
            redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)
        }

        // 4. 원자적 이름 변경
        redisTemplate.rename(KEY_HOT_COMMENTS_TEMP, KEY_HOT_COMMENTS)
        log.info("{}개의 인기 댓글을 Redis에 성공적으로 동기화!", hotComments.size)
    }
}