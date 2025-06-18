![image](https://github.com/user-attachments/assets/df6ab3b4-184f-4e64-8a74-2425de45bf69)

# copy-community-chat-review-system

## 프로젝트 개요

이 프로젝트는 커뮤니티 댓글 시스템을 모방한 Spring Boot 기반의 백엔드 서비스입니다. TiDB, Redis, Kafka, XXL-Job 등 다양한 오픈소스 인프라와 연동하여, 실시간 인기 댓글 집계, 동기화, 캐싱, 메시지 브로커, 스케줄링 등 실전 수준의 분산 시스템 구조를 구현합니다.

---

## 주요 기술 스택 및 구조

- **Spring Boot 3.5, Kotlin, JPA**: REST API, DB 연동, 트랜잭션 관리
- **TiDB**: MySQL 호환 분산 DB (댓글 데이터 저장)
- **Redis**: 인기 댓글 캐싱, Sorted Set 활용
- **Kafka**: TiCDC → Kafka → Spring Consumer로 실시간 동기화
- **XXL-Job**: 인기 댓글 주기적 동기화 스케줄러
- **Docker Compose**: 전체 인프라 로컬 구동

---

## 디렉토리 구조

```
├── docker/
│   ├── docker-compose.yml         # 전체 인프라 오케스트레이션
│   └── changefeed.toml            # TiCDC 필터 설정
├── src/
│   ├── main/kotlin/com/sleekydz86/
│   │   ├── domain/comment/        # 댓글 도메인 (Controller, Service, Entity, Repository, DTO)
│   │   └── global/               # 글로벌 설정, Consumer, JobHandler, Config
│   └── resources/
│       ├── application.yml        # Spring, DB, Redis, Kafka, XXL-Job 설정
│       └── messages.properties   # 메시지 번역
├── build.gradle.kts              # Gradle 빌드 스크립트
└── README.md                     # 이 문서
```

---

## Docker 인프라 구동

1. **사전 준비**: Docker, Docker Compose 설치
2. **실행**:
   ```bash
   cd docker
   docker-compose up -d
   ```
3. **구성 요소**:
   - TiDB (PD, TiKV, TiDB)
   - TiCDC (Change Data Capture)
   - Kafka & Zookeeper
   - Redis

> `changefeed.toml` 예시:

```
[filter]
# 필터링 규칙
rules = ['comment_db.t_comment']
```

---

## Spring Boot 애플리케이션 실행

1. **의존성 설치**
   ```bash
   ./gradlew build
   ```
2. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```
   또는
   ```bash
   java -jar build/libs/copy-community-chat-review-system-0.0.1-SNAPSHOT.jar
   ```

---

## 주요 설정 (application.yml)

- **DB**: `jdbc:mysql://localhost:4000/comment_db` (user: sleekydz86, pw: 1234)
- **Redis**: `localhost:6379`, DB 0
- **Kafka**: `localhost:29092`
- **XXL-Job**: `http://127.0.0.1:8080/xxl-job-admin` (포트: 9999)

---

## API 명세

### 1. 댓글 생성

- **POST** `/api/comments`
- **RequestBody**

```json
{
  "content": "댓글 내용",
  "userId": "사용자ID"
}
```

- **Response**

```json
{
  "id": 1,
  "content": "댓글 내용",
  "userId": "사용자ID",
  "likes": 0,
  "createdAt": "2024-06-01T12:34:56",
  "updatedAt": "2024-06-01T12:34:56",
  "message": "댓글이 생성되었습니다"
}
```

### 2. 댓글 좋아요

- **POST** `/api/comments/{id}/like`
- **Response**: `Comment` 객체 (like 수 증가)

### 3. 인기 댓글 조회

- **GET** `/api/comments/hot`
- **Response**: `List<Comment>` (최대 10개)

---

## 핵심 비즈니스 로직 요약

- **댓글 생성/좋아요**: DB 저장 후, Redis Sorted Set(`hot_comments`) 및 캐시(`comment:{id}`) 동기화
- **인기 댓글 조회**: Redis에서 상위 10개 조회, 캐시 미스 시 DB 폴백
- **TiCDC → Kafka → Consumer**: TiDB 변경사항을 Kafka로, Spring Consumer가 Redis에 반영
- **XXL-Job**: 주기적으로 DB에서 인기 댓글을 Redis에 동기화 (핫 데이터 보정)

---

## 메시지/국제화

- `src/main/resources/messages.properties`에서 메시지 관리
  - `comment.created=댓글이 생성되었습니다`
  - `comment.error=댓글 처리 중 오류가 발생했습니다`

---

## 개발/운영 참고

- **테스트**: `@SpringBootTest`로 context load 테스트 기본 제공
- **확장**: 도메인/글로벌 패키지 구조로 기능별 확장 용이
- **설정**: Jackson(LocalDateTime), Redis, XXL-Job 등 커스텀 설정 제공

---

## 참고/문서

- [TiDB 공식문서](https://docs.pingcap.com/)
- [Spring Boot 공식문서](https://spring.io/projects/spring-boot)
- [XXL-Job 공식문서](https://www.xuxueli.com/xxl-job/)
- [Kafka 공식문서](https://kafka.apache.org/)
- [Redis 공식문서](https://redis.io/)

---

## 라이선스

MIT License

포스팅: https://velog.io/@sleekydevzero86/%EB%8C%80%EA%B7%9C%EB%AA%A8-%ED%8A%B8%EB%9E%98%ED%94%BD%EC%9D%84-%EC%9C%84%ED%95%9C-%EB%8B%A4%EC%B8%B5-%EC%8A%A4%ED%86%A0%EB%A6%AC%EC%A7%80-%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98-%EC%98%A4%ED%94%88%EC%86%8C%EC%8A%A4-%EA%B5%AC%ED%98%84-1
