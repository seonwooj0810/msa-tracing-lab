package lab.tracing.order;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.otlp.tracing.export.enabled=false", "clients.read-timeout=100ms"})
@AutoConfigureObservability
class OrderControllerTest {
    static final HttpServer product = server();
    static final AtomicInteger calls = new AtomicInteger();
    static volatile String mode = "ok";
    static volatile String requestedPath;
    static volatile String traceparent;
    @Autowired TestRestTemplate http;

    static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                requestedPath = exchange.getRequestURI().toString();
                traceparent = exchange.getRequestHeaders().getFirst("traceparent");
                if (mode.equals("disconnect")) { exchange.close(); return; }
                if (mode.equals("slow")) {
                    try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                byte[] body = "{\"id\":100,\"name\":\"MSA 학습 도서\",\"price\":30000}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(mode.equals("error") ? 503 : 200, body.length);
                    exchange.getResponseBody().write(body);
                } finally { exchange.close(); }
            });
            server.start();
            return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("clients.product.base-url", () -> "http://127.0.0.1:" + product.getAddress().getPort());
    }
    @BeforeEach void reset() { mode = "ok"; calls.set(0); }
    @AfterAll static void stop() { product.stop(0); }

    @Test void enrichesOrderWithProductOverHttp() {
        var response = http.getForEntity("/orders?memberId=1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("""
            [{"id":1000,"memberId":1,"product":{"id":100,"name":"MSA 학습 도서","price":30000},"quantity":1}]
            """.trim());
        assertThat(requestedPath).isEqualTo("/products/100");
        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }
    @Test void rejectsUnknownMemberWithoutProductCall() {
        assertThat(http.getForEntity("/orders?memberId=999", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(calls.get()).isZero();
    }
    @Test void reportsProductHttpFailure() { mode = "error"; assertGatewayFailure(); }
    @Test void reportsProductConnectionFailure() { mode = "disconnect"; assertGatewayFailure(); }
    @Test void boundsProductReadTime() {
        mode = "slow";
        long start = System.nanoTime();
        assertGatewayFailure();
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1500);
    }
    void assertGatewayFailure() {
        var response = http.getForEntity("/orders?memberId=1", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().get("traceId").toString()).matches("[0-9a-f]{32}");
    }
}
