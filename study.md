# 📚 백엔드 취업 준비생을 위한 핵심 학습 가이드 (Study.md)

코딩 감각을 되찾고 최신 트렌드를 마스터하기 위해 이 프로젝트를 진행하며 **반드시 공부하고 넘어가야 할 핵심 개념들**입니다. 개발을 진행하며 헷갈리는 부분은 이 문서를 나침반 삼아 학습하세요.

## 1. 최신 Java & Spring Boot 트렌드
- **Java 21 핵심 기능:**
  - `Virtual Threads (가상 스레드)`: 기존 OS 스레드 대비 매우 가벼워 수만 개의 동시 접속을 처리하기 유리합니다. WebFlux(비동기) 없이도 Spring MVC에서 높은 동시성을 처리하는 방법을 학습하세요.
  - `Record`: DTO(Data Transfer Object)를 만들 때 불필요한 보일러플레이트 코드(Getter, Setter, Constructor)를 없애주는 레코드 클래스 사용법.
  - `Pattern Matching for switch`: 더 간결하고 안전해진 switch 문법.
- **Spring Boot 3.x의 변화:**
  - Java 17 이상 필수 지원.
  - `Jakarta EE 10` 적용 (패키지명이 `javax.*`에서 `jakarta.*`로 변경된 점 주의).

## 2. 실시간 데이터 통신 전략 (REST vs SSE vs WebSocket)
- **REST API:** 클라이언트가 요청(Request)할 때만 서버가 응답(Response). (예: 로그인, 내 자산 조회, 매수 주문 접수).
- **SSE (Server-Sent Events):** 서버가 클라이언트에게 **단방향**으로 계속 데이터를 푸시. (예: 백엔드에서 갱신된 실시간 코인 시세를 브라우저로 쏴줄 때 적합).
- **WebSocket:** 클라이언트와 서버가 **양방향**으로 실시간 통신. (예: 실시간 채팅방).
  > 💡 *학습 포인트:* 시세 데이터는 서버->클라이언트 단방향 전송이 주 목적이므로 SSE가 가벼울 수 있습니다. 각 기술의 장단점을 비교해 보세요.

## 3. 동시성 제어 (Concurrency Control)
여러 유저가 동시에 접근하거나, 한 유저가 매수 버튼을 다다닥 눌렀을 때 데이터(예수금, 잔고)가 꼬이지 않게 막는 기술입니다.
- **낙관적 락 (Optimistic Lock):** 데이터 수정 시 버전(Version)을 확인하여 충돌이 났을 때만 애플리케이션 단에서 재시도 처리.
- **비관적 락 (Pessimistic Lock):** DB의 락(Select ... For Update)을 사용하여 원천적으로 다른 트랜잭션의 접근을 차단.
- **분산 락 (Distributed Lock - Redis):** 서버가 여러 대일 때 Redis를 활용해 락을 잡는 방법. (Redisson 라이브러리).
  > 💡 *학습 포인트:* 내 주문 로직에는 어떤 락이 가장 적절할지 고민하고 코드에 적용해 봅니다.

## 4. 대용량 트래픽 처리와 Redis
- **랭킹 시스템 병목 현상:** 10만 명의 유저 수익률을 DB에서 `ORDER BY`로 정렬하면 DB가 뻗을 수 있습니다.
- **Redis Sorted Set (ZSET):** Redis는 인메모리(메모리 상주) 데이터베이스라 엄청 빠릅니다. ZSET은 데이터를 넣음과 동시에 정렬된 상태를 유지해 줍니다.
  > 💡 *학습 포인트:* DB와 Redis 간의 데이터 동기화 방법(배치 작업 등)과 Redis 기본 명령어(`ZADD`, `ZREVRANGE`)를 학습하세요.

## 5. 성능 테스트 및 더미 데이터 (Testing & Dummy Data)
상용 서비스의 가치를 증명하려면 "트래픽이 몰려도 안정적이다"를 보여줘야 합니다.
- **JPA Batch Insert:** 더미 유저 10만 명, 더미 거래내역 100만 건을 넣을 때 건당 Insert하면 하루 종일 걸립니다. JdbcTemplate을 활용한 Bulk Insert 기법을 학습하세요.
- **테스트 코드 (Test Code):**
  - `JUnit 5`와 `Mockito`를 활용해 주문 서비스(OrderService)의 순수 비즈니스 로직을 검증하는 단위 테스트 작성법.
  - Testcontainers를 활용해 로컬 환경과 무관하게 독립적인 DB(Redis, MySQL) 통합 테스트 환경 구축.

## 6. 객체지향과 클린 코드 (Clean Code)
- 아무리 좋은 기술을 써도 코드가 더러우면 탈락입니다.
- **계층 분리:** Controller(요청/응답 처리) - Service(비즈니스 로직) - Repository(데이터 접근) 간의 역할 명확히 분리.
- **조기 리턴 (Early Return):** if-else 중첩을 피하고 예외 상황을 먼저 검증하여 튕겨내는 코딩 스타일 연습.
