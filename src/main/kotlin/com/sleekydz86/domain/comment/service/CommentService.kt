package com.sleekydz86.domain.comment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sleekydz86.domain.comment.entity.Comment
import com.sleekydz86.domain.comment.repository.CommentRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService {

    companion object {
        private val log = LoggerFactory.getLogger(CommentService::class.java) // 로거
        private const val KEY_HOT_COMMENTS = "hot_comments" // 인기 댓글 키
        private const val KEY_COMMENT_CACHE_PREFIX = "comment:" // 댓글 캐시 키 접두사
    }

    @Autowired
    private lateinit var commentRepository: CommentRepository // 댓글 리포지토리

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any> // Redis 템플릿

    @Autowired
    private lateinit var objectMapper: ObjectMapper // JSON 매핑 객체

    @Transactional
    fun createComment(content: String, userId: String): Comment {
        val comment = Comment().apply {
            this.content = content
            this.userId = userId
            // likes, version, createdAt, updatedAt는 기본값 또는 @PrePersist로 처리됨
        }
        return commentRepository.save(comment)
    }

    @Transactional
    fun likeComment(commentId: Long): Comment {
        // 낙관적 락을 사용하여 좋아요 처리
        val comment = commentRepository.findById(commentId)
            .orElseThrow { EntityNotFoundException("댓글이 존재하지 않음: $commentId") }
        comment.likes = comment.likes + 1
        // save는 @Version이 있을 경우 버전 번호를 자동으로 체크하며, 충돌 시 예외 발생
        return commentRepository.save(comment)
    }

    fun getTop10HotComments(): List<Comment> {
        try {
            // 1. Redis의 Sorted Set에서 상위 10개 댓글 ID 조회
            val commentIds = redisTemplate.opsForZSet().reverseRange(KEY_HOT_COMMENTS, 0, 9)

            if (commentIds.isNullOrEmpty()) {
                log.warn("Redis 인기 댓글 목록이 비어 있음, 데이터베이스에서 직접 조회!")
                return fetchFromDB()
            }

            // 2. ID를 기반으로 Redis 캐시에서 댓글 상세 정보 일괄 조회
            val cacheKeys = commentIds.map { id -> "$KEY_COMMENT_CACHE_PREFIX$id" }
            val cachedCommentsJson = redisTemplate.opsForValue().multiGet(cacheKeys) ?: emptyList()

            // 3. 역직렬화 및 캐시 미스 처리
            return cachedCommentsJson.mapNotNull { json ->
                try {
                    // 캐시 히트 시 역직렬화
                    if (json != null) {
                        objectMapper.readValue(json as String, Comment::class.java)
                    } else {
                        // 캐시 미스 시 (예: 캐시 만료), null 반환
                        // 실제 프로덕션에서는 데이터베이스에서 조회하는 로직 추가 필요
                        null
                    }
                } catch (e: Exception) {
                    log.error("Redis에서 댓글 역직렬화 실패", e)
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Redis에서 인기 댓글 목록 조회 실패, 데이터베이스에서 조회!", e)
            // 예외 발생 시 데이터베이스에서 조회
            return fetchFromDB()
        }
    }

    // 데이터베이스에서 직접 조회 (폴백 메서드)
    private fun fetchFromDB(): List<Comment> {
        return commentRepository.findTop10ByOrderByLikesDesc()
    }
}