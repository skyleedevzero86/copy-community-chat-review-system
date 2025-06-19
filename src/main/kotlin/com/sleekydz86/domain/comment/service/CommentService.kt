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
import java.util.concurrent.TimeUnit

@Service
class CommentService {

    companion object {
        private val log = LoggerFactory.getLogger(CommentService::class.java)
        private const val KEY_HOT_COMMENTS = "hot_comments"
        private const val KEY_COMMENT_CACHE_PREFIX = "comment:"
    }

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Transactional
    fun createComment(content: String, userId: String): Comment {
        val comment = Comment().apply {
            this.content = content
            this.userId = userId
        }
        val savedComment = commentRepository.save(comment)

        // Redis에도 저장 (새 댓글이므로 좋아요 수는 0)
        try {
            redisTemplate.opsForZSet().add(KEY_HOT_COMMENTS, savedComment.id.toString(), 0.0)
            val cacheKey = "$KEY_COMMENT_CACHE_PREFIX${savedComment.id}"
            val commentJson = objectMapper.writeValueAsString(savedComment)
            redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)
            log.info("새 댓글 Redis 저장 완료: ID = {}", savedComment.id)
        } catch (e: Exception) {
            log.error("새 댓글 Redis 저장 실패: {}", e.message, e)
        }

        return savedComment
    }

    @Transactional
    fun likeComment(commentId: Long): Comment {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { EntityNotFoundException("댓글이 존재하지 않음: $commentId") }
        comment.likes = comment.likes + 1
        val updatedComment = commentRepository.save(comment)

        try {
            // Sorted Set 업데이트 (좋아요 수로 점수 설정)
            redisTemplate.opsForZSet().add(KEY_HOT_COMMENTS, updatedComment.id.toString(), updatedComment.likes.toDouble())

            // 캐시 업데이트 (TTL 1시간)
            val cacheKey = "$KEY_COMMENT_CACHE_PREFIX${updatedComment.id}"
            val commentJson = objectMapper.writeValueAsString(updatedComment)
            redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)

            log.info("Redis 업데이트 성공 - 댓글 ID: {}, 좋아요 수: {}", updatedComment.id, updatedComment.likes)
        } catch (e: Exception) {
            log.error("Redis 업데이트 실패: {}", e.message, e)
        }

        return updatedComment
    }

    fun getTop10HotComments(): List<Comment> {
        try {
            // Redis Sorted Set에서 상위 10개 댓글 ID 조회 (점수 높은 순)
            val commentIds = redisTemplate.opsForZSet().reverseRange(KEY_HOT_COMMENTS, 0, 9)

            if (commentIds.isNullOrEmpty()) {
                log.warn("Redis 인기 댓글 목록이 비어 있음, 데이터베이스에서 조회")
                return fetchFromDB()
            }

            // Redis 캐시에서 댓글 상세 정보 조회
            val comments = mutableListOf<Comment>()
            val missingIds = mutableListOf<String>()

            commentIds.forEach { id ->
                val cacheKey = "$KEY_COMMENT_CACHE_PREFIX$id"
                try {
                    val cachedJson = redisTemplate.opsForValue().get(cacheKey) as? String
                    if (cachedJson != null) {
                        val comment = objectMapper.readValue(cachedJson, Comment::class.java)
                        comments.add(comment)
                    } else {
                        missingIds.add(id.toString())
                    }
                } catch (e: Exception) {
                    log.error("댓글 ID {} 역직렬화 실패", id, e)
                    missingIds.add(id.toString())
                }
            }

            // 캐시 미스된 댓글들을 DB에서 조회하여 캐시 보충
            if (missingIds.isNotEmpty()) {
                val missingComments = commentRepository.findAllById(missingIds.map { it.toLong() })
                missingComments.forEach { comment ->
                    try {
                        val cacheKey = "$KEY_COMMENT_CACHE_PREFIX${comment.id}"
                        val commentJson = objectMapper.writeValueAsString(comment)
                        redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)
                        comments.add(comment)
                    } catch (e: Exception) {
                        log.error("캐시 보충 실패: 댓글 ID {}", comment.id, e)
                    }
                }
            }

            // 좋아요 수 기준으로 정렬하여 반환
            return comments.sortedByDescending { it.likes }

        } catch (e: Exception) {
            log.error("Redis에서 인기 댓글 조회 실패, DB 폴백", e)
            return fetchFromDB()
        }
    }

    // 데이터베이스 폴백 메서드
    private fun fetchFromDB(): List<Comment> {
        return try {
            commentRepository.findTop10ByOrderByLikesDesc()
        } catch (e: Exception) {
            log.error("데이터베이스에서도 댓글 조회 실패", e)
            emptyList()
        }
    }
}