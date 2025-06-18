package com.sleekydz86.domain.comment.dto

import java.time.LocalDateTime

data class CommentResponse(
    val id: Long?,
    val content: String?,
    val userId: String?,
    val likes: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val message: String
)