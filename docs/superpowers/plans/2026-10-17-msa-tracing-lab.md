# 순차 RestClient 분산 추적 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution, or superpowers:subagent-driven-development if the user selects delegation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 순차 RestClient 호출의 W3C 컨텍스트 전파와 서비스별 처리 시간을 Jaeger와 로그에서 확인한다.

**Architecture:** 독립 실행되는 BFF, 회원, 주문, 상품 서비스로 구성한다. BFF는 회원 조회를 마친 뒤 주문을 조회하고, 주문 서비스는 상품을 조회한다. Spring Boot의 자동 HTTP 계측과 Micrometer OTel bridge를 사용해 OTLP HTTP로 Jaeger에 전송한다.

**Tech Stack:** Java 21, Spring Boot 3.5 계열 공개 릴리스, Gradle 8.14.3 Wrapper, Spring MVC, RestClient, Micrometer Tracing, OpenTelemetry OTLP exporter, JUnit 5, Docker Compose, Jaeger 2 계열.

**Spec:** `docs/superpowers/specs/2026-10-17-msa-tracing-lab-design.md`

## Global Constraints

- HTTP 클라이언트는 RestClient를 사용하며 첫 버전은 동기 순차 호출로 구성한다.
- Micrometer Tracing으로 W3C Trace Context를 전파한다.
- 외부 진입점은 `GET /api/mypage/1`이다.
- 예제 데이터는 회원 1, 상품 100, 주문 1000으로 고정한다. 지원하지 않는 식별자는 404로 처리한다.
- BFF 성공 응답에는 `traceId`, `member`, `orders`를 포함한다.
- 각 서비스가 소유한 응답 record를 사용하고 공통 비즈니스 DTO 라이브러리는 만들지 않는다.
- propagation은 W3C로 명시하고 로컬 샘플링 비율은 1.0으로 설정한다.
- 부분 응답이나 재시도는 수행하지 않는다. 회원 호출에 실패하면 주문 호출을 시작하지 않는다.
- README에는 기동, curl 예제, 응답 traceId 조회, Jaeger 타임라인 해석, 로그 검색, 종료 방법을 한국어로 작성한다.
- 생성된 `.idea/` 등 사용자 IDE 파일은 수정하거나 커밋하지 않는다.

## Review Focus

1. 외부 traceparent가 없거나 잘못된 경우: 유효한 새 traceId를 만들고 요청을 정상 처리한다. Task 3의 실제 HTTP 검증에 포함한다.
2. 회원 서비스 실패: BFF가 주문 호출을 시작하지 않고 traceId가 있는 502를 반환한다. Task 2에서 요청 기록으로 검증한다.
3. 상품 응답 지연: 제한된 읽기 타임아웃 이후 502가 BFF까지 전파된다. Task 2에서 지연 HTTP 서버로 검증한다.
4. 미지원 식별자: 로컬 API는 404, 다운스트림 통신 문제는 502로 구분한다. Task 1과 Task 2에서 상태와 응답 본문을 검증한다.
5. OTLP 배치 전송 지연: 요청 직후 trace가 없더라도 제한 시간 내 폴링해 확인하고, 제한 시간 초과는 검증 실패로 처리한다. Task 3 검증 스크립트에 포함한다.

## 파일과 책임

- `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: 네 모듈의 빌드, 공통 의존성 및 테스트 실행 설정.
- 각 모듈의 `src/main/java/lab/tracing/{bff,member,order,product}/*Application.java`: 독립 애플리케이션 진입점.
- `member-service/.../MemberController.java`, `product-service/.../ProductController.java`: 고정 데이터와 404 처리.
- `bff/.../MypageController.java`, `MypageService.java`, `DownstreamClients.java`: API, 순차 조합, 자동 설정 Builder 기반 HTTP 클라이언트.
- `order-service/.../OrderController.java`, `OrderService.java`, `ProductClient.java`: 주문 응답과 상품 HTTP 조회.
- 각 모듈의 `TraceLogFilter.java`, `ApiExceptionHandler.java`, `application.yml`: DEBUG 수신 헤더 로그, 오류 응답, 서비스 이름과 추적 설정. 필요한 서비스에만 HTTP 클라이언트 설정을 둔다.
- 각 모듈의 `src/test/java/lab/tracing/.../*Test.java`: API·호출 순서·실패 검증.
- `Dockerfile`, `compose.yaml`, `.dockerignore`, `.gitignore`: 서비스 이미지, Jaeger, 실행 환경.
- `scripts/verify-trace.py`, `README.md`: 실제 trace 검증과 한국어 실습 가이드.

## Task 1: 빌드와 회원·상품 HTTP API

**Interfaces:** `GET /members/{id}` → `Member(long id, String name)`; `GET /products/{id}` → `Product(long id, String name, long price)`. 회원 이름은 `선우`, 상품명은 `MSA 학습 도서`, 가격은 `30000`으로 고정한다. 로컬 오류 응답은 `ApiError(String traceId, String message)`다.

- [ ] 공개 Maven 저장소에서 Boot 3.5 릴리스를 확인해 버전을 고정하고 Wrapper 및 네 모듈 빌드 설정을 작성한다. Java toolchain은 21, 테스트는 JUnit Platform이다.
- [ ] `MemberControllerTest`, `ProductControllerTest`에 정상 데이터와 미지원 ID 404 테스트를 먼저 작성한다. HTTP API가 없어 실패하는 것을 확인한다.
- [ ] 두 API와 애플리케이션 진입점, Micrometer/OTLP 의존성, W3C 설정을 구현한다.
- [ ] `./gradlew :member-service:test :product-service:test`로 정상 데이터와 404 테스트 성공을 확인한다.
- [ ] 관련 파일만 커밋한다.

## Task 2: 주문 조회와 BFF 순차 조합

**Interfaces:** 주문 응답은 각 서비스가 소유한 `Order(long id, long memberId, Product product, int quantity)`의 배열이며 수량은 1이다. `GET /orders?memberId=1`에서 주문 1000과 상품 100을 반환한다. BFF는 `MypageResponse(String traceId, Member member, List<Order> orders)`를 반환한다. `MypageService.get(long memberId)`와 `OrderService.find(long memberId)`는 각각 위 응답과 목록을 반환한다.

**HTTP configuration:** `clients.member.base-url`, `clients.order.base-url`, `clients.product.base-url`로 대상을 지정한다. 연결 타임아웃 1초, 읽기 타임아웃 3초를 기본값으로 하며 테스트에서 더 짧게 덮어쓸 수 있도록 설정 프로퍼티로 둔다. RestClient는 주입받은 Builder를 clone해 서비스별로 생성한다.

- [ ] `OrderControllerTest`에 실제 로컬 HTTP 상품 stub을 사용한 상품 정보 조합, 미지원 회원 404, 상품 연결 실패 및 지연 시 502 테스트를 작성하고 실패를 확인한다.
- [ ] `OrderController`, `OrderService`, `ProductClient`, 오류 처리와 상품 호출 전후 로그를 구현하고 `./gradlew :order-service:test`를 통과시킨다.
- [ ] `MypageControllerTest`에 실제 로컬 HTTP stub을 사용한 조합 응답 테스트를 작성한다. 회원 응답을 latch로 지연하는 동안 주문 요청이 없음을 확인하고, 회원 응답 이후 요청을 허용해 최종 응답을 검증한다.
- [ ] 회원 연결 실패 시 주문 요청이 0건이며 응답이 502인지, 주문 실패 시 502인지, 오류 본문에 유효한 traceId가 있는지 검증하는 테스트를 작성해 실패를 확인한다.
- [ ] `MypageController`, `MypageService`, `DownstreamClients`, 오류 처리와 호출 전후 로그를 구현한다. 미지원 BFF ID는 HTTP 호출 전 404로 처리한다.
- [ ] 네 서비스의 DEBUG 필터에서 수신 traceparent를 기록한다. 로그 correlation에 서비스 이름, traceId, spanId를 포함한다. 예외 처리에서 tracer의 현재 span을 사용한다.
- [ ] `./gradlew test`로 전체 테스트를 통과시키고 관련 파일을 커밋한다.

## Task 3: Compose와 실제 trace 검증 및 가이드

**Interfaces:** `docker compose up --build -d`로 네 서비스와 Jaeger를 실행한다. 포트는 8080/8081/8082/8083, Jaeger UI 16686과 OTLP HTTP 4318이다. `python3 scripts/verify-trace.py`는 실제 요청을 보내 trace 검색과 span 관계 검증에 성공하면 0, 실패하면 1로 종료한다. 스크립트는 Python 표준 라이브러리만 사용한다.

- [ ] Docker Registry에서 Jaeger 2 계열 이미지 태그를 확인해 고정한다. Compose 이름은 `msa-tracing-lab`, trace 저장은 학습용 메모리 저장소로 구성한다. Dockerfile은 모듈 이름을 build argument로 받는다.
- [ ] `scripts/verify-trace.py`에 정상 응답의 traceId와 네 서비스의 span, W3C 외부 traceId 유지, 잘못된 헤더에서 새 trace 생성, 부모 관계, 회원 client span 종료 후 주문 client span 시작을 검증하는 조건을 작성한다. Jaeger 검색은 최대 30초 폴링하고 실패하면 명확한 오류로 종료한다.
- [ ] `./gradlew clean build`, `docker compose config --quiet`를 실행한다.
- [ ] `docker compose up --build -d` 이후 readiness를 확인하고 검증 스크립트를 실행한다. 기본 요청에서 server 4개와 client 3개가 하나의 trace에 포함되는지 확인한다.
- [ ] 상품 서비스만 중지한 후 BFF 요청의 502와 traceId, Jaeger 오류 span을 확인한다. 상품 서비스를 다시 시작해 정상 응답 복구를 확인한다.
- [ ] README에 전체 기동과 종료, 로컬 bootRun 4개 명령, 기본 curl, 고정 traceparent curl, DEBUG 설정, 로그 traceId 검색, Jaeger 타임라인과 client/server span 설명, 장애 실습을 작성한다. 메모리 trace는 Jaeger 종료 시 삭제됨을 설명한다.
- [ ] `git diff --check`와 최종 전체 테스트를 실행한다. 실행하지 못한 항목이 있으면 실제 환경 제약과 함께 기록한다. 관련 파일을 커밋한다.

## 실행 방식 제안

세 작업이 HTTP 응답 모델과 실행 설정을 공유하므로 현재 세션에서 직접 순서대로 구현하는 방식을 권장한다. 새 프로젝트 전용 저장소에서 작업하므로 현재 checkout에서 구현하며, 별도 worktree가 필요하면 생성 전에 사용자 선호를 확인한다.
