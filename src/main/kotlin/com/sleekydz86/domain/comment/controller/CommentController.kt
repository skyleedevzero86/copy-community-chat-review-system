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

@RestController
@RequestMapping("/api/comments")
class CommentController(
    private val commentService: CommentService
) {
    @Autowired
    private lateinit var messageSource: MessageSource

    @PostMapping
    fun createComment(@RequestBody request: CreateCommentRequest): ResponseEntity<CommentResponse> {
        val content = request.content ?: throw IllegalArgumentException("Content cannot be null")
        val userId = request.userId ?: throw IllegalArgumentException("User ID cannot be null")

        val comment = commentService.createComment(content, userId)
        val message = messageSource.getMessage("comment.created", null, Locale("ko", "KR"))
        return ResponseEntity(CommentResponse(comment.id, comment.content, comment.userId, comment.likes, comment.createdAt, comment.updatedAt, message), HttpStatus.CREATED)
    }

    @PostMapping("/{id}/like")
    fun likeComment(@PathVariable id: Long): ResponseEntity<Comment> {
        return try {
            val comment = commentService.likeComment(id)
            ResponseEntity.ok(comment)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @GetMapping("/hot")
    fun getHotComments(): ResponseEntity<List<Comment>> {
        val comments = commentService.getTop10HotComments()
        return ResponseEntity.ok(comments)
    }
}