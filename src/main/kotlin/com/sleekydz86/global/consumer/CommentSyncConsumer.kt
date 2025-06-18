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

        private const val KEY_HOT_COMMENTS = "hot_comments" // 인기 댓글 키
        private const val KEY_COMMENT_CACHE_PREFIX = "comment:" // 댓글 캐시 키 접두사

        // TiDB 시간 형식 파서 (yyyy-MM-dd HH:mm:ss)
        private val TIDB_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    @Autowired
    private lateinit var objectMapper: ObjectMapper // JSON 매핑 객체

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, Any> // Redis 템플릿

    @KafkaListener(topics = ["comment-topic"], groupId = "comment-sync-group-final")
    fun listen(messageBytes: ByteArray?) {
        // 메시지가 없거나 빈 경우 (하트비트 메시지) 무시
        if (messageBytes == null || messageBytes.isEmpty()) {
            return
        }

        val originalMessage = String(messageBytes, StandardCharsets.UTF_8)
        var jsonPayload = ""
        try {
            // JSON 객체 시작 위치 찾기
            val jsonStart = originalMessage.indexOf('{')
            if (jsonStart == -1) {
                log.warn("수신된 메시지에 유효한 JSON 객체가 포함되지 않음. 메시지: {}", originalMessage)
                return
            }
            jsonPayload = originalMessage.substring(jsonStart)

            log.info("정리된 유효 JSON 메시지: {}", jsonPayload)
            val rootNode: JsonNode = objectMapper.readTree(jsonPayload)

            val (eventType, dataNode) = when {
                rootNode.has("u") -> "UPSERT" to rootNode.get("u")
                rootNode.has("d") -> "DELETE" to rootNode.get("d")
                else -> {
                    log.warn("알 수 없는 JSON 형식 (u 또는 d 없음). 메시지: {}", jsonPayload)
                    return
                }
            }

            val comment = Comment().apply {
                // JSON의 'v' 값 추출
                id = dataNode.get("id").get("v").asLong()
                content = dataNode.get("content").get("v").asText()

                // JSON의 user_id를 엔티티의 userId로 매핑
                userId = dataNode.get("user_id").get("v").asText()

                likes = dataNode.get("likes").get("v").asInt()
                version = dataNode.get("version").get("v").asInt()

                // 문자열을 LocalDateTime으로 변환
                val createdAtStr = dataNode.get("created_at").get("v").asText()
                val updatedAtStr = dataNode.get("updated_at").get("v").asText()

                createdAt = LocalDateTime.parse(createdAtStr, TIDB_DATETIME_FORMATTER)
                updatedAt = LocalDateTime.parse(updatedAtStr, TIDB_DATETIME_FORMATTER)
            }

            log.info("성공적으로 파싱된 Comment 객체: {}", comment)

            // 이벤트 타입에 따른 비즈니스 로직
            when (eventType) {
                "UPSERT" -> {
                    comment.id?.let { commentId ->
                        redisTemplate.opsForZSet().add(KEY_HOT_COMMENTS, commentId.toString(), comment.likes.toDouble())
                        log.info("Redis 인기 댓글 목록 업데이트: Comment ID = {}, Likes = {}", commentId, comment.likes)

                        val cacheKey = "$KEY_COMMENT_CACHE_PREFIX$commentId"
                        val commentJson = objectMapper.writeValueAsString(comment)
                        redisTemplate.opsForValue().set(cacheKey, commentJson, 1, TimeUnit.HOURS)
                        log.info("Redis 캐시 업데이트: Key = {}", cacheKey)
                    }
                }
                "DELETE" -> {
                    comment.id?.let { commentId ->
                        redisTemplate.opsForZSet().remove(KEY_HOT_COMMENTS, commentId.toString())
                        log.info("Redis 인기 댓글 목록에서 제거: Comment ID = {}", commentId)

                        redisTemplate.delete("$KEY_COMMENT_CACHE_PREFIX$commentId")
                        log.info("Redis 캐시에서 삭제: Key = {}", "$KEY_COMMENT_CACHE_PREFIX$commentId")
                    }
                }
            }

        } catch (e: Exception) {
            log.error("Kafka 메시지 처리 중 알 수 없는 오류 발생! 원본 메시지: [{}], 처리 시도한 JSON: [{}]", originalMessage, jsonPayload, e)
        }
    }
}