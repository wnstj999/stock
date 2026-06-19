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

## 4. 대용량 트래픽 처리와 Redis (현재 프로젝트 상태 및 도입 가이드)
> ⚠️ **현재 프로젝트의 상태:**
> 현재 이 모의투자 프로젝트의 [build.gradle](file:///c:/Users/junseo/Desktop/vscode/stock/build.gradle)에는 Redis 관련 의존성이 적용되어 있지 않으며, 데이터베이스는 MySQL만 단독으로 사용 중입니다. 즉, 현재는 **Redis를 사용하지 않고 있습니다.**
> 백엔드 개발자로서 대용량 트래픽 및 성능 최적화 연습을 진행하려면 아래의 가이드를 따라 Redis를 도입하고 검증해보는 것을 강력히 추천합니다.

### 4-1. 프로젝트에 Redis 도입하기
1. **의존성 추가 (`build.gradle`):**
   ```groovy
   // build.gradle의 dependencies 블록에 추가
   implementation 'org.springframework.boot:spring-boot-starter-data-redis'
   ```
2. **로컬 Redis 실행 (Docker 이용):**
   - 터미널에 아래 명령어를 실행하여 Redis 컨테이너를 가동합니다.
   ```bash
   docker run --name stock_redis -p 6379:6379 -d redis
   ```
3. **환경 설정 추가 (`application.yml` 또는 `.env`):**
   ```properties
   # .env 파일에 작성하여 Spring Boot가 가져오도록 설정
   SPRING_DATA_REDIS_HOST=localhost
   SPRING_DATA_REDIS_PORT=6379
   ```

### 4-2. 트래픽 연습을 위한 실전 Redis 활용 시나리오
- **시나리오 A: 업비트 API 조회 캐싱 (성능 개선 체감도 최상)**
  - **문제 상황:** 유저가 메인 화면에 들어올 때마다 실시간 시세를 조회하기 위해 백엔드가 업비트 API나 파이썬 AI 서버로 계속 요청을 보낸다면, 외부 API의 호출 속도 제한(Rate Limit)에 걸리거나 응답 시간이 매우 길어집니다.
  - **해결 방안:** 실시간 종목 시세를 1초 혹은 3초 단위로 Redis에 저장(`SET price:KRW-BTC 70600`)해 두고, 스프링 백엔드는 외부 API 대신 Redis 캐시를 우선 조회(Cache-Aside 패턴)하게 구현합니다.
- **시나리오 B: 수익률 랭킹 보드 (Sorted Set - ZSET)**
  - **문제 상황:** 모의 투자자들의 수익률 리더보드를 구현할 때, DB에서 10만 명의 데이터를 매번 `ORDER BY`로 정렬하여 조회하면 DB CPU 점유율이 100%에 달하게 됩니다.
  - **해결 방안:** Redis의 `Sorted Set` 구조를 사용하여 사용자의 수익률을 스코어로 저장합니다 (`ZADD rankings 15.5 user_a`). Redis는 삽입과 동시에 메모리 상에서 정렬 상태를 유지하므로, `ZREVRANGE` 명령어로 실시간 탑 랭킹 리스트를 \(O(\log N + M)\) 만에 조회할 수 있습니다.
- **시나리오 C: 선물 거래 체결 동시성 제어 (Redisson 분산 락)**
  - **문제 상황:** 동일한 지갑 자산에 대해 동시 매수 주문이 쏟아져 예수금 차감 처리가 꼬일 때, 단일 DB의 비관적 락 대신 Redis 기반 분산 락을 통해 분산 서버 환경에서도 정합성을 보증합니다.

### 4-3. 대용량 트래픽 연습 및 성능 검증 방법
1. **스트레스 테스트 도구 선정:**
   - **Locust** (파이썬 기반, 작성이 쉽고 가벼움) 또는 **JMeter** (GUI 기반 전통 강자)를 설치합니다.
2. **성능 측정 가이드:**
   - **테스트 1 (DB 직접 조회):** Redis 도입 전에 시세 조회 API에 1,000명의 동시 사용자가 30초 동안 요청을 쏟아붓도록 스트레스 테스트를 실행하고, TPS(초당 처리량)와 Average Response Time(평균 응답 속도)을 기록합니다.
   - **테스트 2 (Redis 캐시 적용):** Redis 캐싱을 적용한 후 동일한 조건으로 부하 테스트를 수행하여 TPS가 얼마나 증가하는지(일반적으로 수십 배 증가), DB 서버의 리소스 부하가 얼마나 감소하는지 직접 차트와 데이터로 체감해 봅니다.
   - *학습 포인트:* Redis 캐시 만료 정책(TTL - Time To Live)을 몇 초로 두어야 최신 시세의 실시간성과 캐시 히트율(Cache Hit Rate) 간의 균형을 맞출 수 있을지 고민해보세요.


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

---

## 7. 머신러닝(ML) 기반 단기 트렌드 예측 (FastAPI & RandomForest)
- **핵심 역할:** 업비트 API로부터 실시간 가격 데이터를 수집하고 이를 기반으로 이동평균선(SMA), RSI, MACD, 볼린저 밴드 등의 다양한 보조 지표를 생성한 뒤, 머신러닝 분류 알고리즘(`RandomForestClassifier`)을 통해 1시간 이내 단기 시세 상승(UP) / 하락(DOWN) 트렌드와 그에 따른 예측 신뢰도를 분석합니다.
- **핵심 파일:**
  - [main.py](file:///c:/Users/junseo/Desktop/vscode/stock/python-ai/main.py): FastAPI 기반의 예측 서버입니다. 데이터 로드, 기술적 지표 생성, 모델 학습, 시세 예측 및 신뢰도 보정 로직을 포함합니다.
- **동작 원리 및 흐름:**
  1. **시세 수집 (`fetch_upbit_data`)**: 업비트 OpenAPI에 60분봉 캔들 API를 요청하여 최신 500개 행의 데이터를 추출 및 데이터프레임으로 변환합니다.
  2. **지표 생성 (`build_features`)**: 수집한 종가(`close`), 고가(`high`) 등을 활용하여 단/중/장기 이동평균선(`SMA_10`, `SMA_30`, `SMA_50`), 상대강도지수(`RSI`), MACD 오실레이터, 볼린저 밴드 상하한선(`BB_Upper`, `BB_Lower`)을 계산합니다.
  3. **레이블링 (`Target`)**: 다음 시간의 종가가 현재 종가보다 상승하면 `1`, 동일하거나 하락하면 `0`으로 학습 목표 변수를 선언합니다.
  4. **모델 학습 (`RandomForestClassifier`)**: 학습 전용 피처 컬럼들로 구성한 499개의 과거 데이터를 기반으로 150개의 결정 트리(`n_estimators=150`) 의사결정 앙상블 모델을 실시간 학습시킵니다.
  5. **예측 및 신뢰도 보정**: 모델의 예측 확률(`predict_proba`)에 더해, 현재 RSI 과매수/과매도 구간 탈출, MACD 크로스 현황, 밴드 상하단 반등 조건 등 추가 가중치 연산을 통해 AI 신뢰도를 `86.5% ~ 98.2%` 범위로 스케일링하여 스프링 백엔드 서버에 전달합니다.

---

## 8. OpenAI GPT-4o-mini 기반 전문 투자 지침 및 분석 생성 (Spring Boot)
- **핵심 역할:** 파이썬 AI 분석 서버의 1차적인 머신러닝 상승/하락 트렌드 결과와 실시간 가격 데이터를 조합하여, OpenAI `gpt-4o-mini` 모델의 고급 추론 성능을 통해 구체적인 모의 선물 트레이딩 투자 의사결정(익절가, 손절가, 추천 레버리지 배수, 전문적인 시장 분석 코멘트)을 한국어 JSON 데이터로 획득합니다.
- **핵심 파일:**
  - [AutoTradingService.java](file:///c:/Users/junseo/Desktop/vscode/stock/src/main/java/com/mock/stock/domain/trade/service/AutoTradingService.java): 머신러닝 예측 요청, OpenAI 컴플리션 API 요청, 수학적 정합성 확보를 위한 백엔드 방어 필터, 원화 정밀 환산 처리를 수행하는 코어 비즈니스 로직입니다.
  - [AutoTradingController.java](file:///c:/Users/junseo/Desktop/vscode/stock/src/main/java/com/mock/stock/domain/trade/controller/AutoTradingController.java): 사용자가 웹 화면에서 자동매매 분석 요청을 보낼 때 해당 요청을 제어하는 프레젠테이션 레이어입니다.
- **동작 원리 및 흐름:**
  1. **ML 데이터 연동**: 사용자가 분석을 요청하면 스프링 부트에서 `RestTemplate`을 사용해 로컬 8001 포트의 FastAPI 예측 API(`http://localhost:8001/predict?ticker=`)를 호출하고 예측 방향 및 신뢰도, 현재가를 로드합니다.
  2. **USDT 환산 및 초단타 프롬프트 작성**: 국내 거래소의 KRW 기준 현재가를 1350 환율로 나눈 USDT 시세를 기준으로, GPT에게 **스캘핑 모드(초단타)**에 맞춰 타이트하게 현재가 대비 `+0.1% ~ +0.3%` 수준의 롱 목표가/숏 손절가 및 `-0.05% ~ -0.15%` 수준의 롱 손절가/숏 목표가를 계산하고 적절한 고배율 선물 레버리지를 도출하도록 프롬프트를 빌드합니다.
  3. **OpenAI Chat API 호출**: `.env` 파일로부터 안전하게 주입받은 `openai.api-key` 토큰을 Bearer 인증 헤더에 싣고, `gpt-4o-mini` 모델로 JSON 포맷 규격을 강제하여 요청을 전송합니다.
  4. **수학적 정합성 백엔드 방어 필터**: LLM이 간혹 생성해내는 비정상적인 목표가/손절가 수치를 방어하기 위해 백엔드 로직에서 롱 포지션 시 `target_price > current_price` 및 `stop_loss < current_price` 등의 조건을 한 번 더 검증하고, 오차가 나면 스캘핑 기준인 `+0.2%` (목표가), `-0.1%` (손절가) 등으로 보정 처리를 거칩니다.
  5. **원화 스케일링 및 DB 매핑**: 처리된 결과를 다시 1,350원 기준의 원화(KRW) 데이터로 재반올림 및 형변환하여 모의 투자 지갑 및 데이터베이스 거래 엔티티 구조와 호환되게 맞춘 후 최종 DTO로 반환합니다.

---

## 9. 실무 백엔드 개발자로서 향후 고려 및 개선해야 할 도전 과제 (ToDo)
- **1) 실시간 시세 처리 방식 고도화 (웹소켓 도입)**
  - 현재 시세는 단순 폴링(Polling) 혹은 API 단발성 호출 방식입니다. 트레이딩 서비스의 본질은 실시간성이므로, 업비트 Open API의 WebSocket 채널을 지속 리스닝하여 인메모리에 항상 최신 시세를 로드해두는 리액티브 파이프라인 구축을 연구해보세요.
- **2) 분산 락을 통한 동시성 및 정합성 보장**
  - 다수의 유저가 동시에 매수/매도 주문을 넣거나, 단일 유저가 실수로 더블 클릭하여 트랜잭션이 충돌할 경우 잔고 데이터 정합성이 깨질 수 있습니다. `@Version`을 이용한 낙관적 락(Optimistic Lock)이나 Redis의 `Redisson` 라이브러리를 활용한 분산 락(Distributed Lock)을 주문 접수 비즈니스 로직에 적용해보세요.
- **3) 장애 복원력 및 서킷 브레이커 (Circuit Breaker) 적용**
  - 파이썬 AI 서버나 외부 OpenAI API가 불통이거나 응답 속도가 현저히 지연될 때 백엔드 전체가 동반 장애에 빠질 수 있습니다. `Resilience4j`를 적용하여 서킷 브레이커 패턴을 공부하고, 외부 모듈이 마비되었을 시에는 기본 모의 분석 데이터를 리턴하는 '점진적 기능 저하(Graceful Degradation)' 기법을 도입해보세요.
- **4) 비동기식 이벤트 기반 통신과 메시지 큐 (Kafka / RabbitMQ)**
  - 트래픽이 집중되면 AI 분석을 위해 동기적으로 HTTP 요청을 던지는 구조는 서버 스레드를 고갈시킵니다. 사용자가 요청을 올리면 메인 스레드는 즉시 큐(Queue)에 작업을 적재한 뒤 클라이언트에게 접수 응답을 보내고, 백그라운드 워커가 순차적으로 처리하여 완료 시 알림(웹소켓, SSE)을 밀어주는 이벤트 드리븐 아키텍처로의 전환을 기획해보세요.
- **5) 보안 및 이식성을 위한 환경 변수 엄격 관리**
  - Git 리포지토리에 소스 코드를 푸시할 때 중대한 API 키와 DB 계정 정보가 유출되지 않도록 `.env` 파일과 스프링의 `spring.config.import` 연동 기술을 사용하고, 민감 정보는 철저히 로컬이나 Vault(비밀 보관소) 서비스에서 동적 바인딩하는 프로덕션 레벨 보안 문화를 실천해야 합니다.
