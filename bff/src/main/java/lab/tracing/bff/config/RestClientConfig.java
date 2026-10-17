package lab.tracing.bff.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import lab.tracing.bff.mypage.infrastructure.member.MemberClient;
import lab.tracing.bff.mypage.infrastructure.order.OrderClient;

@Configuration
class RestClientConfig {

	@Bean
	MemberClient memberClient(
		RestClient.Builder builder,
		@Value("${clients.member.base-url}") String baseUrl,
		@Value("${clients.connect-timeout}") Duration connectTimeout,
		@Value("${clients.read-timeout}") Duration readTimeout
	) {
		return createClient(MemberClient.class, builder, baseUrl, connectTimeout, readTimeout);
	}

	@Bean
	OrderClient orderClient(
		RestClient.Builder builder,
		@Value("${clients.order.base-url}") String baseUrl,
		@Value("${clients.connect-timeout}") Duration connectTimeout,
		@Value("${clients.read-timeout}") Duration readTimeout
	) {
		return createClient(OrderClient.class, builder, baseUrl, connectTimeout, readTimeout);
	}

	private <T> T createClient(
		Class<T> clientType,
		RestClient.Builder builder,
		String baseUrl,
		Duration connectTimeout,
		Duration readTimeout
	) {
		var requestFactory = new JdkClientHttpRequestFactory(
			HttpClient.newBuilder().connectTimeout(connectTimeout).build()
		);
		requestFactory.setReadTimeout(readTimeout);
		// Keep Boot's ObservationRegistry and tracing customizations on the injected builder.
		RestClient restClient = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
			.build()
			.createClient(clientType);
	}
}
