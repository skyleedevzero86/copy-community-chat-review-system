package com.sleekydz86.domain.comment.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "t_comment")
data class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null, // 댓글 ID

    var content: String? = null, // 댓글 내용

    var userId: String? = null, // 사용자 ID

    var likes: Int = 0, // 좋아요 수, 기본값 0

    @Column(name = "created_at", updatable = false)
    var createdAt: LocalDateTime? = null, // 생성 시간

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null, // 수정 시간

    @Version // 중요! 낙관적 락 버전 번호 필드
    var version: Int = 0 // 버전, 기본값 0
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L // 직렬화 버전 UID
    }

    @PrePersist
    protected fun onCreate() {
        this.createdAt = LocalDateTime.now() // 생성 시 현재 시간 설정
        this.updatedAt = LocalDateTime.now() // 생성 시 수정 시간도 현재 시간으로 설정
    }

    @PreUpdate
    protected fun onUpdate() {
        this.updatedAt = LocalDateTime.now() // 수정 시 현재 시간으로 갱신
    }
}