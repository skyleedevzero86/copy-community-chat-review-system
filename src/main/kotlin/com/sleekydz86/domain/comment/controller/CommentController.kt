package com.sleekydz86.domain.comment.controller

import com.sleekydz86.domain.comment.dto.CommentResponse
import com.sleekydz86.domain.comment.dto.CreateCommentRequest
import com.sleekydz86.domain.comment.entity.Comment
import com.sleekydz86.domain.comment.service.CommentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.beans.factory.annotation.Autowired
import java.util.Locale
import org.springframework.context.MessageSource
import org.slf4j.LoggerFactory

@RestController
@RequestMapping("/api/comments")
class CommentController(
    private val commentService: CommentService
) {

    companion object {
        private val log = LoggerFactory.getLogger(CommentController::class.java)
    }

    @Autowired
    private lateinit var messageSource: MessageSource

    // 댓글 생성 - POST /api/comments
    @PostMapping
    fun createComment(@RequestBody request: CreateCommentRequest): ResponseEntity<CommentResponse> {
        log.info("댓글 생성 요청: content={}, userId={}", request.content, request.userId)

        val content = request.content ?: throw IllegalArgumentException("Content cannot be null")
        val userId = request.userId ?: throw IllegalArgumentException("User ID cannot be null")

        val comment = commentService.createComment(content, userId)
        val message = try {
            messageSource.getMessage("comment.created", null, Locale("ko", "KR"))
        } catch (e: Exception) {
            "댓글이 생성되었습니다." // 기본 메시지
        }

        return ResponseEntity(
            CommentResponse(
                comment.id,
                comment.content,
                comment.userId,
                comment.likes,
                comment.createdAt,
                comment.updatedAt,
                message
            ),
            HttpStatus.CREATED
        )
    }

    // 좋아요 - POST /api/comments/{id}/like
    @PostMapping("/{id}/like")
    fun likeComment(@PathVariable id: Long): ResponseEntity<Comment> {
        log.info("댓글 좋아요 요청: commentId={}", id)

        return try {
            val comment = commentService.likeComment(id)
            log.info("댓글 좋아요 성공: commentId={}, likes={}", id, comment.likes)
            ResponseEntity.ok(comment)
        } catch (e: Exception) {
            log.error("댓글 좋아요 실패: commentId={}", id, e)
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    // 인기 댓글 조회 - GET /api/comments/hot
    @GetMapping("/hot")
    fun getHotComments(): ResponseEntity<List<Comment>> {
        log.info("인기 댓글 조회 요청")

        return try {
            val comments = commentService.getTop10HotComments()
            log.info("인기 댓글 조회 성공: {}개", comments.size)
            ResponseEntity.ok(comments)
        } catch (e: Exception) {
            log.error("인기 댓글 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    // 특정 댓글 조회 - GET /api/comments/{id}
    @GetMapping("/{id}")
    fun getComment(@PathVariable id: Long): ResponseEntity<Comment> {
        log.info("댓글 조회 요청: commentId={}", id)

        return try {
            ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
        } catch (e: Exception) {
            log.error("댓글 조회 실패: commentId={}", id, e)
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    // 전역 예외 처리
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        log.warn("잘못된 요청: {}", e.message)
        return ResponseEntity.badRequest().body(mapOf(
            "error" to "Bad Request",
            "message" to (e.message ?: "잘못된 요청입니다.")
        ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<Map<String, String>> {
        log.error("서버 오류", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
            "error" to "Internal Server Error",
            "message" to "서버에서 오류가 발생했습니다."
        ))
    }
}