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
        var response = http.getForEntity("/members/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("""
            {"id":1,"name":"선우"}
            """.trim());
    }

    @Test void rejectsUnknownId() {
        assertThat(http.getForEntity("/members/999", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
