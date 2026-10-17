package lab.tracing.bff;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class DownstreamClients {
    private final RestClient members;
    private final RestClient orders;
    DownstreamClients(RestClient.Builder builder,
                      @Value("${clients.member.base-url}") String memberUrl,
                      @Value("${clients.order.base-url}") String orderUrl,
                      @Value("${clients.connect-timeout}") Duration connectTimeout,
                      @Value("${clients.read-timeout}") Duration readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        this.members = builder.clone().baseUrl(memberUrl).requestFactory(factory).build();
        this.orders = builder.clone().baseUrl(orderUrl).requestFactory(factory).build();
    }
    Member member(long memberId) {
        Member member = members.get().uri("/members/{id}", memberId).retrieve().body(Member.class);
        if (member == null) throw new RestClientException("Empty member response");
        return member;
    }
    List<Order> orders(long memberId) {
        List<Order> result = orders.get().uri("/orders?memberId={id}", memberId).retrieve()
            .body(new ParameterizedTypeReference<List<Order>>() {});
        if (result == null) throw new RestClientException("Empty order response");
        return result;
    }
}
