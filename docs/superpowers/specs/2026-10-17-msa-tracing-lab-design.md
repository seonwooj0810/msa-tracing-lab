# RestClient 기반 MSA 분산 추적 학습 프로젝트

## 목표와 확정된 요구사항

- BFF가 여러 다운스트림을 호출하는 Java/Spring 학습 프로젝트를 만든다.
- HTTP 클라이언트는 RestClient를 사용하며 첫 버전은 동기 순차 호출로 구성한다.
- Micrometer Tracing으로 W3C Trace Context를 전파한다.
- 하나의 traceId로 서비스별 로그와 Jaeger의 요청 흐름을 연결해 확인한다.

## 첫 버전의 구성

별도 저장소 `msa-tracing-lab`에 Gradle 멀티 프로젝트를 구성한다. Java 21, Spring Boot 3.5 계열의 공개 릴리스, Spring MVC를 사용한다. 학습용으로 널리 쓰이는 Boot 3 계열을 선택하며 정확한 의존성과 컨테이너 버전은 구현 시 실제 저장소에서 확인해 고정한다.

| 애플리케이션 | 로컬 포트 | 역할 |
|---|---|---|
| bff | 8080 | 회원 정보와 주문 내역을 조합해 마이페이지 반환 |
| member-service | 8081 | 고정된 회원 정보 반환 |
| order-service | 8082 | 고정 주문에 상품 정보를 추가해 반환 |
| product-service | 8083 | 고정된 상품 정보 반환 |
| Jaeger | UI 16686, OTLP HTTP 4318 | trace 수집과 호출 타임라인 조회 |

애플리케이션은 서로 독립된 프로세스로 실행한다. 첫 버전은 데이터베이스, 인증, 메시지 브로커, 서비스 디스커버리, 재시도, 병렬 실행을 포함하지 않는다. 각 서비스가 소유한 응답 record를 사용하고 공통 비즈니스 DTO 라이브러리는 만들지 않는다.

## 요청 흐름과 API

외부 진입점은 `GET /api/mypage/1`이다.

1. BFF가 회원 서비스의 `GET /members/1`을 호출하고 응답을 기다린다.
2. 회원 조회에 성공하면 BFF가 주문 서비스의 `GET /orders?memberId=1`을 호출한다.
3. 주문 서비스가 상품 서비스의 `GET /products/100`을 호출하고 응답을 기다린다.
4. 주문 서비스가 상품 정보를 포함한 주문 한 건을 반환한다.
5. BFF가 회원 정보와 주문 목록을 합쳐 반환한다.

BFF 성공 응답에는 `traceId`, `member`, `orders`를 포함한다. 학습자가 응답의 traceId를 복사해 Jaeger에서 검색할 수 있도록 한다. 예제 데이터는 회원 1, 상품 100, 주문 1000으로 고정한다. 지원하지 않는 식별자는 404로 처리한다.

## 추적과 로그

- 각 서비스에 Actuator, `micrometer-tracing-bridge-otel`, OTLP exporter를 적용한다.
- propagation은 W3C로 명시하고 로컬 샘플링 비율은 1.0으로 설정한다.
- 서비스 이름은 각각의 `spring.application.name`으로 구분한다.
- HTTP 클라이언트는 Spring Boot가 자동 설정한 `RestClient.Builder`를 주입받아 생성한다. traceparent를 직접 복사하거나 수동 traceId 헤더로 대체하지 않는다.
- 유효한 외부 traceparent가 없으면 BFF에서 trace를 시작한다. 유효한 외부 traceparent가 있으면 해당 trace를 이어간다.
- 서비스마다 들어오는 HTTP 요청은 server span, 나가는 RestClient 호출은 client span으로 관찰한다. 정상 요청은 기본적으로 server span 4개와 client span 3개를 만든다.
- 모든 span은 같은 traceId를 공유하지만 spanId는 각 구간마다 다르며 부모 span 관계로 연결된다.
- 로그에 서비스 이름, traceId, spanId를 표시한다. BFF의 회원 호출 전후와 주문 호출 전후, 주문 서비스의 상품 호출 전후를 기록해 순차 호출을 확인한다.
- 수신 traceparent는 로컬 학습을 위한 DEBUG 로그에서만 확인할 수 있게 한다.
- 애플리케이션은 Jaeger의 OTLP HTTP `/v1/traces`로 직접 전송한다. Collector는 첫 버전에서 제외한다.

## 실패 처리

각 RestClient에 연결 및 읽기 타임아웃을 지정한다. 다운스트림 연결 실패 또는 타임아웃은 상위 호출자에게 502로 반환하고, 오류 응답에는 현재 traceId를 포함한다. 로컬 API의 미지원 식별자는 404다. 부분 응답이나 재시도는 수행하지 않는다. 회원 호출에 실패하면 주문 호출을 시작하지 않는다. 상품 호출에 실패하면 주문 서비스와 BFF까지 실패가 전파된다.

## 실행과 학습 자료

- Gradle Wrapper로 빌드와 테스트를 실행할 수 있게 한다.
- Docker Compose로 네 애플리케이션과 Jaeger를 함께 실행한다.
- 로컬 Java 프로세스와 Compose의 Jaeger를 조합하는 실행 방법도 제공한다.
- README에는 기동, curl 예제, 응답 traceId 조회, Jaeger 타임라인 해석, 로그 검색, 종료 방법을 한국어로 작성한다.
- 외부에서 고정 traceparent를 전달하는 curl 예제로 동일 traceId가 유지되는지 실습한다.
- 상품 서비스를 중지하고 요청을 보내 오류 전파를 관찰하는 실습을 제공한다.
- 컨테이너와 포트 충돌을 피할 수 있도록 이 프로젝트 전용 Compose 이름을 사용한다.

## 검증과 완료 기준

1. 전체 Gradle 빌드 및 서비스 테스트가 성공한다.
2. BFF 테스트에서 회원 응답을 받은 후에 주문 호출을 시작하는지 검증한다.
3. 네 서비스와 Jaeger를 실행한 실제 HTTP 요청으로 응답 내용을 확인한다.
4. Jaeger에서 응답 traceId의 trace가 조회되고 네 서비스의 span이 포함되는지 확인한다.
5. client/server 부모 관계와 회원 호출 완료 이후 주문 호출 시작을 확인한다.
6. 외부에서 지정한 유효한 traceId가 네 서비스에 유지되는지 확인한다.
7. 상품 서비스 장애 시 502와 traceId가 반환되는지 확인한다.

실행 환경 제약으로 수행하지 못한 검증은 수행한 검증과 구분해 보고한다.

## 참고 문서

- https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html
- https://www.w3.org/TR/trace-context/
- https://www.jaegertracing.io/docs/2.17/getting-started/
