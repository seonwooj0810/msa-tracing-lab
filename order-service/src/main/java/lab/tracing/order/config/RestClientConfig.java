package lab.tracing.order.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import lab.tracing.order.order.infrastructure.product.ProductClient;

@Configuration
class RestClientConfig {

	@Bean
	ProductClient productClient(
		RestClient.Builder builder,
		@Value("${clients.product.base-url}") String baseUrl,
		@Value("${clients.connect-timeout}") Duration connectTimeout,
		@Value("${clients.read-timeout}") Duration readTimeout
	) {
		var requestFactory = new JdkClientHttpRequestFactory(
			HttpClient.newBuilder().connectTimeout(connectTimeout).build()
		);
		requestFactory.setReadTimeout(readTimeout);
		// Keep Boot's ObservationRegistry and tracing customizations on the injected builder.
		RestClient restClient = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
			.build()
			.createClient(ProductClient.class);
	}
}
