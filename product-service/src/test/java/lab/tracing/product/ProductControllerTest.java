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
		var response = http.getForEntity("/api/v1/products/100", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("""
			{"success":true,"data":{"id":100,"name":"MSA 학습 도서","price":30000}}
			""".trim());
	}

	@Test void rejectsUnknownId() {
		var response = http.getForEntity("/api/v1/products/999", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":1001", "상품을 찾을 수 없습니다.");
	}
}
