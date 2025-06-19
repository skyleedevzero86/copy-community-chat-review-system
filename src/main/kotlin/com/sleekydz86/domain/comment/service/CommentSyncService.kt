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
        private val log = LoggerFactory.getLogger(CommentSyncService::class.java)
        private const val KEY_HOT_COMMENTS = "hot_comments"
        private const val KEY_HOT_COMMENTS_TEMP = "hot_comments_temp"
        private const val KEY_COMMENT_CACHE_PREFIX = "comment:"
    }

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Transactional(readOnly = true)
    fun syncHotCommentsToRedis() {
        log.info("인기 댓글 Redis 동기화 시작...")

        try {
            // 1. 동기화 범위 정의 (최근 7일)
            val sevenDaysAgo = LocalDateTime.now().minusDays(7)
            val pageRequest = PageRequest.of(0, 1000)

            // 2. TiDB에서 인기 댓글 조회
            val hotComments = commentRepository.findTopLikedCommentsSince(sevenDaysAgo, pageRequest)
            if (hotComments.isEmpty()) {
                log.info("동기화할 인기 댓글이 없음")
                return
            }
            log.info("데이터베이스에서 {}개의 인기 댓글 조회", hotComments.size)

            // 3. 임시 키 초기화
            redisTemplate.delete(KEY_HOT_COMMENTS_TEMP)

            // 4. 배치로 Redis에 저장
            hotComments.forEach { comment ->
                try {
                    // Sorted Set에 추가 (좋아요 수를 점수로 사용)
                    redisTemplate.opsForZSet().add(
                        KEY_HOT_COMMENTS_TEMP,
                        comment.id.toString(),
                        comment.likes.toDouble()
                    )

                    // 개별 댓글 캐시 저장
                    val cacheKey = "$KEY_COMMENT_CACHE_PREFIX${comment.id}"
                    val commentJson = objectMapper.writeValueAsString(comment)
                    redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)

                } catch (e: Exception) {
                    log.error("댓글 ID {} Redis 저장 실패", comment.id, e)
                }
            }

            // 5. 원자적 키 교체 (데이터 일관성 보장)
            if (redisTemplate.hasKey(KEY_HOT_COMMENTS_TEMP)) {
                redisTemplate.rename(KEY_HOT_COMMENTS_TEMP, KEY_HOT_COMMENTS)
                log.info("{}개의 인기 댓글 Redis 동기화 완료", hotComments.size)
            } else {
                log.warn("임시 키가 존재하지 않음, 동기화 실패")
            }

        } catch (e: Exception) {
            log.error("인기 댓글 Redis 동기화 실패", e)
            // 임시 키 정리
            try {
                redisTemplate.delete(KEY_HOT_COMMENTS_TEMP)
            } catch (cleanupEx: Exception) {
                log.error("임시 키 정리 실패", cleanupEx)
            }
            throw e // 상위로 예외 전파
        }
    }
}