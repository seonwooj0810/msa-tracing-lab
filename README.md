# MSA Tracing Lab

BFF가 `RestClient`로 여러 서비스를 **순차 호출**하고, Micrometer Tracing으로 W3C Trace Context를 전파하는 학습 프로젝트입니다.

Java 21 · Spring Boot 3.5.16 · Gradle 8.14.3 · Spring MVC · Micrometer Tracing + OpenTelemetry · Jaeger 2.17.0

## 요청 흐름

`GET /api/mypage/1`을 호출하면 BFF가 회원 조회를 완료한 뒤 주문을 조회합니다. 주문 서비스는 상품 조회를 완료한 뒤 응답합니다.

```text
Client
  └─ BFF :8080
       ├─ 1. 회원 서비스 :8081 → 응답 대기
       └─ 2. 주문 서비스 :8082
               └─ 상품 서비스 :8083 → 응답 대기
          → 주문 응답 대기
       → 회원 + 주문 결과 조합

각 서비스 ── OTLP HTTP ──→ Jaeger :4318/v1/traces
                            └─ 조회 UI :16686
```

데이터베이스 없이 회원 1, 주문 1000, 상품 100의 고정 데이터를 사용합니다.

## 빠르게 실행

Docker와 Docker Compose가 필요합니다. Java는 이미지 내부에서 빌드하므로 전체 Compose 실행에는 로컬 JDK가 없어도 됩니다.

```bash
docker compose up --build -d --wait
curl -s http://localhost:8080/api/mypage/1
```

최초 실행은 JDK 이미지와 Gradle 의존성을 내려받아 시간이 걸립니다. 상태는 `docker compose ps`, 로그는 `docker compose logs -f`로 확인합니다.

응답 예시에서 `traceId`는 요청마다 바뀝니다.

```json
{
  "traceId": "32자리 16진수 ID",
  "member": { "id": 1, "name": "선우" },
  "orders": [
    {
      "id": 1000,
      "memberId": 1,
      "product": { "id": 100, "name": "MSA 학습 도서", "price": 30000 },
      "quantity": 1
    }
  ]
}
```

다른 프로젝트가 기본 포트를 사용 중이면 호스트 포트를 바꿀 수 있습니다. 컨테이너 간 주소는 바뀌지 않습니다.

```bash
BFF_PORT=18080 MEMBER_PORT=18081 ORDER_PORT=18082 PRODUCT_PORT=18083 \
JAEGER_UI_PORT=26686 OTLP_PORT=14318 docker compose up --build -d --wait
```

포트를 바꿨다면 이후 Compose 명령에도 같은 환경변수를 적용하세요. 검증 스크립트에는 `--bff-url http://localhost:18080 --jaeger-url http://localhost:26686`을 넘깁니다.

## Jaeger에서 호출 흐름 확인

1. [Jaeger UI](http://localhost:16686)를 엽니다.
2. 응답의 `traceId`를 UI의 trace ID 조회 입력창에서 검색합니다. 또는 `http://localhost:16686/trace/{traceId}`로 바로 이동합니다.
3. HTTP server/client span을 펼쳐 서비스 이름, 부모 관계, 시작 시간, 소요 시간을 확인합니다.

span은 배치 전송되므로 응답 직후에는 몇 초 기다려야 할 수 있습니다. 기본 정상 요청은 **server span 4개 + client span 3개 = 7개**입니다.

```text
bff SERVER
  ├─ bff CLIENT → 회원 조회
  │    └─ member-service SERVER
  └─ bff CLIENT → 주문 조회
       └─ order-service SERVER
            └─ order-service CLIENT → 상품 조회
                 └─ product-service SERVER
```

타임라인에서 **회원 CLIENT span 종료 후 주문 CLIENT span이 시작**하는 것을 확인하세요. server span과 client span은 동일 호출의 서로 다른 측정 구간이므로 소요 시간을 단순 합산하면 중복 계산됩니다. 상품 호출이 느려지면 이를 기다리는 주문과 BFF의 span도 함께 길어집니다.

## traceId, spanId, W3C 헤더

- `traceId`: 전체 요청 흐름에서 유지되는 32자리 16진수 ID입니다.
- `spanId`: 각 처리 구간마다 생성되는 16자리 16진수 ID입니다.
- `traceparent`: 서비스 간 HTTP 요청으로 컨텍스트를 넘기는 W3C 헤더입니다.

```text
traceparent: 00-{traceId 32자리}-{parentId 16자리}-01
```

`parentId`는 해당 HTTP 요청을 보내는 **client span의 ID**입니다. 수신 서비스는 별도 server span을 만들고 그 client span을 부모로 연결합니다. 수신 헤더를 그대로 다음 서비스로 복사하면 이 관계를 제대로 표현할 수 없습니다.

클라이언트가 유효한 헤더를 주면 BFF가 기존 trace를 이어갑니다. 헤더가 없거나 잘못된 경우 새 trace를 만듭니다. `tracestate`는 벤더별 부가 정보이며 이 실습에서 직접 작성할 필요가 없습니다.

매번 새 ID로 외부 trace를 시작하는 예시입니다.

```bash
trace_id=$(python3 -c 'import secrets; print(secrets.token_hex(16))')
curl -s http://localhost:8080/api/mypage/1 \
  -H "traceparent: 00-${trace_id}-0123456789abcdef-01"
echo "$trace_id"
```

응답 traceId가 입력 traceId와 같아야 합니다. 같은 ID를 반복 사용하면 여러 요청이 하나의 trace에 합쳐지므로 실습마다 새 ID를 사용하세요.

## 로그와 실제 헤더 확인

모든 서비스의 요청 처리 로그에 서비스 이름, traceId, spanId를 남깁니다. 애플리케이션 기동 등 요청 외부의 로그는 traceId가 비어 있습니다.

```text
[bff,traceId=...,spanId=...] Calling member-service memberId=1
[bff,traceId=...,spanId=...] Completed member-service memberId=1
[bff,traceId=...,spanId=...] Calling order-service memberId=1
[order-service,traceId=...,spanId=...] Calling product-service productId=100
[product-service,traceId=...,spanId=...] Returning product productId=100
[order-service,traceId=...,spanId=...] Completed product-service productId=100
[bff,traceId=...,spanId=...] Completed order-service memberId=1
```

BFF의 호출 전후 로그는 BFF server span 안에서 기록됩니다. 실제 송신 client span의 ID는 Jaeger에서 확인합니다.

```bash
# 복사한 실제 traceId로 검색
docker compose logs --no-color | rg '복사한_traceId'

# 수신 traceparent 헤더를 DEBUG 로그로 확인
TRACE_LOG_LEVEL=DEBUG docker compose up -d --wait
curl -s http://localhost:8080/api/mypage/1
docker compose logs --no-color | rg 'Incoming|Calling|Completed'
```

`rg`가 없다면 `grep`을 사용할 수 있습니다. DEBUG를 끄려면 `TRACE_LOG_LEVEL=INFO docker compose up -d --wait`를 실행합니다.

## 자동 검증

Python 3 표준 라이브러리만 사용합니다. 네 서비스가 healthy가 된 뒤 실행하세요.

```bash
./gradlew test
python3 -m unittest discover -s scripts/tests -v
python3 scripts/verify-trace.py
```

실제 BFF 요청과 Jaeger 데이터를 검사합니다.

- 새 trace, 외부 W3C trace 유지, 잘못된 traceparent에서 새 trace 생성
- 네 서비스의 7개 span, 고유 spanId와 동일 traceId
- HTTP client/server span의 부모 연결
- 회원 조회 완료 이후 주문 조회 시작
- 응답의 회원·주문·상품 데이터

unit/integration 테스트에서는 로컬 HTTP stub으로 회원 응답을 지연시켜 주문 호출이 시작되지 않는지 확인하며, 다운스트림 오류와 읽기 타임아웃도 검증합니다.

## 장애 전파 실습

```bash
docker compose stop product-service
curl -i http://localhost:8080/api/mypage/1
python3 scripts/verify-trace.py --expect-error

# 복구
docker compose start product-service
```

BFF 응답은 `502 Bad Gateway`와 `traceId`, `message`를 포함합니다. 해당 trace에서는 상품 server span이 없고 주문 서비스의 상품 client span에 오류가 나타납니다. 주문 서비스와 BFF도 502로 끝납니다.

연결 타임아웃은 1초, 읽기 타임아웃은 3초이며 `CLIENT_CONNECT_TIMEOUT`, `CLIENT_READ_TIMEOUT`으로 변경할 수 있습니다. BFF와 주문 서비스의 읽기 제한이 같으므로 느린 상품 호출에서는 BFF 타임아웃이 주문 서비스의 오류 응답보다 먼저 발생할 수도 있습니다. 재시도와 부분 응답은 수행하지 않습니다.

로컬 API의 지원하지 않는 회원·상품 ID는 404입니다. 회원 조회가 실패하면 주문 조회를 시작하지 않습니다.

## IDE 또는 로컬 Java 실행

로컬 JDK 21을 준비하고, 전체 Compose 실행 중이라면 먼저 내려 포트 충돌을 피합니다.

```bash
docker compose down
docker compose up -d jaeger
./gradlew clean build
```

다음 명령은 **각각 별도 터미널**에서 실행합니다. 서비스별 기본 포트와 다운스트림 주소는 `application.yml`에 설정되어 있습니다.

```bash
./gradlew :product-service:bootRun
./gradlew :member-service:bootRun
./gradlew :order-service:bootRun
./gradlew :bff:bootRun
```

IntelliJ에서는 Gradle 프로젝트로 열고 각 모듈의 `*Application` main을 실행해도 됩니다. DEBUG 헤더 로그는 실행 환경변수 `LOGGING_LEVEL_LAB_TRACING=DEBUG`로 켭니다.

## 코드에서 볼 지점

| 파일 | 학습 포인트 |
|---|---|
| `bff/.../MypageService.java` | 회원 → 주문의 동기 순차 호출과 응답 조합 |
| `bff/.../DownstreamClients.java` | 주입받은 RestClient.Builder로 HTTP 계측 유지 |
| `order-service/.../ProductClient.java` | 다운스트림에서 다시 다른 서비스로 trace 전파 |
| 각 모듈 `application.yml` | W3C, 100% 샘플링, OTLP endpoint, 로그 correlation |
| 각 모듈 `TraceLogFilter.java` | 현재 server span 안에서 수신 헤더 확인 |
| `scripts/verify-trace.py` | 실제 trace의 관계와 시간 검증 |

`RestClient.create()` 또는 직접 생성한 Builder로 바꾸면 Spring Boot가 제공하는 자동 계측 설정이 빠질 수 있습니다. 현재 코드는 **주입받은 Builder를 clone**하여 대상 URL과 timeout만 설정합니다. traceparent나 MDC의 traceId를 수동으로 복사하지 않습니다.

## 종료

```bash
docker compose down
```

Jaeger는 학습용 메모리 저장소를 사용하므로 컨테이너 종료·재시작 시 수집된 trace가 삭제됩니다.

## 참고

- [Spring Boot 3.5 Tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [Jaeger 2.17 시작하기](https://www.jaegertracing.io/docs/2.17/getting-started/)
