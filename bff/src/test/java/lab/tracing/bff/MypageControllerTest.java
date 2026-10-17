package lab.tracing.bff;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.otlp.tracing.export.enabled=false", "clients.read-timeout=2s"})
@AutoConfigureObservability
class MypageControllerTest {
    static final HttpServer downstream = server();
    static final AtomicInteger memberCalls = new AtomicInteger();
    static final AtomicInteger orderCalls = new AtomicInteger();
    static volatile boolean blockMember;
    static volatile int memberStatus = 200;
    static volatile int orderStatus = 200;
    static volatile CountDownLatch memberEntered = new CountDownLatch(1);
    static volatile CountDownLatch releaseMember = new CountDownLatch(1);
    static volatile CountDownLatch orderEntered = new CountDownLatch(1);
    static volatile String orderPath;
    static volatile String memberTraceparent;
    static volatile String orderTraceparent;
    @Autowired TestRestTemplate http;

    static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                var t = new Thread(r); t.setDaemon(true); return t;
            }));
            server.createContext("/", exchange -> {
                boolean member = exchange.getRequestURI().getPath().startsWith("/members/");
                if (member) {
                    memberCalls.incrementAndGet();
                    memberTraceparent = exchange.getRequestHeaders().getFirst("traceparent");
                    memberEntered.countDown();
                    if (blockMember) {
                        try { releaseMember.await(3, TimeUnit.SECONDS); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                } else {
                    orderCalls.incrementAndGet();
                    orderPath = exchange.getRequestURI().toString();
                    orderTraceparent = exchange.getRequestHeaders().getFirst("traceparent");
                    orderEntered.countDown();
                }
                String json = member ? "{\"id\":1,\"name\":\"선우\"}"
                    : "[{\"id\":1000,\"memberId\":1,\"product\":{\"id\":100,\"name\":\"MSA 학습 도서\",\"price\":30000},\"quantity\":1}]";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(member ? memberStatus : orderStatus, body.length);
                    exchange.getResponseBody().write(body);
                } finally { exchange.close(); }
            });
            server.start(); return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("clients.member.base-url", () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
        registry.add("clients.order.base-url", () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
    }
    @BeforeEach void reset() {
        blockMember = false; memberStatus = 200; orderStatus = 200;
        memberCalls.set(0); orderCalls.set(0);
        memberEntered = new CountDownLatch(1); releaseMember = new CountDownLatch(1); orderEntered = new CountDownLatch(1);
    }
    @AfterEach void release() { releaseMember.countDown(); }
    @AfterAll static void stop() { downstream.stop(0); }

    @Test void waitsForMemberBeforeRequestingOrdersAndCombinesResults() throws Exception {
        blockMember = true;
        var response = CompletableFuture.supplyAsync(() -> http.getForEntity("/api/mypage/1", Map.class));
        try {
            assertThat(memberEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(orderEntered.await(150, TimeUnit.MILLISECONDS)).isFalse();
        } finally { releaseMember.countDown(); }
        var result = response.get(3, TimeUnit.SECONDS);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = result.getBody();
        assertThat(body.get("traceId").toString()).matches("[0-9a-f]{32}");
        assertThat(body.get("member")).isEqualTo(Map.of("id", 1, "name", "선우"));
        assertThat(body.get("orders")).isEqualTo(java.util.List.of(Map.of("id",1000,"memberId",1,
            "product",Map.of("id",100,"name","MSA 학습 도서","price",30000),"quantity",1)));
        assertThat(orderPath).isEqualTo("/orders?memberId=1");
        String traceId = body.get("traceId").toString();
        assertThat(memberTraceparent).startsWith("00-" + traceId + "-");
        assertThat(orderTraceparent).startsWith("00-" + traceId + "-");
        assertThat(memberTraceparent).isNotEqualTo(orderTraceparent);
    }
    @Test void stopsWhenMemberFails() {
        memberStatus = 503;
        assertGatewayFailure();
        assertThat(orderCalls.get()).isZero();
    }
    @Test void returnsGatewayFailureWhenOrdersFail() { orderStatus = 503; assertGatewayFailure(); }
    @Test void rejectsUnknownMemberBeforeCallingDownstream() {
        assertThat(http.getForEntity("/api/mypage/999", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(memberCalls.get()).isZero(); assertThat(orderCalls.get()).isZero();
    }
    void assertGatewayFailure() {
        var result = http.getForEntity("/api/mypage/1", Map.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(result.getBody().get("traceId").toString()).matches("[0-9a-f]{32}");
    }
}
