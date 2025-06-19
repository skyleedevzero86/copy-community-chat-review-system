package com.sleekydz86.global.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sleekydz86.domain.comment.entity.Comment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Service
class CommentSyncConsumer {

    companion object {
        private val log = LoggerFactory.getLogger(CommentSyncConsumer::class.java)
        private const val KEY_HOT_COMMENTS = "hot_comments"
        private const val KEY_COMMENT_CACHE_PREFIX = "comment:"
        private val TIDB_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any>

    @KafkaListener(topics = ["comment-topic"], groupId = "comment-sync-group-final")
    fun listen(messageBytes: ByteArray?) {
        if (messageBytes == null || messageBytes.isEmpty()) {
            return
        }

        val originalMessage = String(messageBytes, StandardCharsets.UTF_8)
        var jsonPayload = ""

        try {
            val jsonStart = originalMessage.indexOf('{')
            if (jsonStart == -1) {
                log.warn("유효하지 않은 JSON 메시지: {}", originalMessage)
                return
            }
            jsonPayload = originalMessage.substring(jsonStart)

            log.info("처리할 JSON 메시지: {}", jsonPayload)
            val rootNode: JsonNode = objectMapper.readTree(jsonPayload)

            val (eventType, dataNode) = when {
                rootNode.has("u") -> "UPSERT" to rootNode.get("u")
                rootNode.has("d") -> "DELETE" to rootNode.get("d")
                else -> {
                    log.warn("알 수 없는 이벤트 타입: {}", jsonPayload)
                    return
                }
            }

            val comment = parseComment(dataNode)
            log.info("파싱된 Comment: ID={}, Content={}, Likes={}", comment.id, comment.content, comment.likes)

            // Redis 업데이트
            when (eventType) {
                "UPSERT" -> handleUpsertEvent(comment)
                "DELETE" -> handleDeleteEvent(comment)
            }

        } catch (e: Exception) {
            log.error("Kafka 메시지 처리 실패 - 원본: [{}], JSON: [{}]", originalMessage, jsonPayload, e)
        }
    }

    private fun parseComment(dataNode: JsonNode): Comment {
        return Comment().apply {
            id = dataNode.get("id").get("v").asLong()
            content = dataNode.get("content").get("v").asText()
            userId = dataNode.get("user_id").get("v").asText()
            likes = dataNode.get("likes").get("v").asInt()
            version = dataNode.get("version").get("v").asInt()

            val createdAtStr = dataNode.get("created_at").get("v").asText()
            val updatedAtStr = dataNode.get("updated_at").get("v").asText()

            createdAt = LocalDateTime.parse(createdAtStr, TIDB_DATETIME_FORMATTER)
            updatedAt = LocalDateTime.parse(updatedAtStr, TIDB_DATETIME_FORMATTER)
        }
    }

    private fun handleUpsertEvent(comment: Comment) {
        try {
            comment.id?.let { commentId ->
                // Sorted Set 업데이트 (좋아요 수를 점수로 사용)
                redisTemplate.opsForZSet().add(
                    KEY_HOT_COMMENTS,
                    commentId.toString(),
                    comment.likes.toDouble()
                )

                // 개별 댓글 캐시 업데이트
                val cacheKey = "$KEY_COMMENT_CACHE_PREFIX$commentId"
                val commentJson = objectMapper.writeValueAsString(comment)
                redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)

                log.info("Redis UPSERT 완료 - ID: {}, Likes: {}", commentId, comment.likes)
            }
        } catch (e: Exception) {
            log.error("Redis UPSERT 실패 - Comment ID: {}", comment.id, e)
        }
    }

    private fun handleDeleteEvent(comment: Comment) {
        try {
            comment.id?.let { commentId ->
                // Sorted Set에서 제거
                redisTemplate.opsForZSet().remove(KEY_HOT_COMMENTS, commentId.toString())

                // 개별 캐시 삭제
                val cacheKey = "$KEY_COMMENT_CACHE_PREFIX$commentId"
                redisTemplate.delete(cacheKey)

                log.info("Redis DELETE 완료 - ID: {}", commentId)
            }
        } catch (e: Exception) {
            log.error("Redis DELETE 실패 - Comment ID: {}", comment.id, e)
        }
    }
}