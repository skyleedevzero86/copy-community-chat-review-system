package com.sleekydz86.domain.comment.repository

import com.sleekydz86.domain.comment.entity.Comment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface CommentRepository : JpaRepository<Comment, Long> {

    // 좋아요 수 기준 상위 10개 댓글 조회
    fun findTop10ByOrderByLikesDesc(): List<Comment>

    /**
     * 지정된 시간 이후 좋아요 수 기준 상위 N개 댓글 조회
     * @param since 기준 시간 (예: 7일 전)
     * @param pageable 페이징 파라미터, 반환 레코드 수 N 제한
     * @return 댓글 목록
     */
    @Query("SELECT c FROM Comment c WHERE c.updatedAt >= :since ORDER BY c.likes DESC")
    fun findTopLikedCommentsSince(since: LocalDateTime, pageable: Pageable): List<Comment>
}