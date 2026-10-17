package lab.tracing.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerTest {
    @Autowired TestRestTemplate http;

    @Test void returnsOwnedData() {
        var response = http.getForEntity("/products/100", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("""
            {"id":100,"name":"MSA 학습 도서","price":30000}
            """.trim());
    }

    @Test void rejectsUnknownId() {
        assertThat(http.getForEntity("/products/999", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
