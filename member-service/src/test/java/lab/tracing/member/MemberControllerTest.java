package lab.tracing.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberControllerTest {
	@Autowired TestRestTemplate http;

	@Test void returnsOwnedData() {
		var response = http.getForEntity("/api/v1/members/1", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("""
			{"success":true,"data":{"id":1,"name":"선우"}}
			""".trim());
	}

	@Test void rejectsUnknownId() {
		var response = http.getForEntity("/api/v1/members/999", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).contains("\"code\":1001", "회원을 찾을 수 없습니다.");
	}
}
