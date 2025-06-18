package com.sleekydz86.domain.comment.dto

data class CreateCommentRequest(
    var content: String? = null, // 댓글 내용
    var userId: String? = null // 사용자 ID
)