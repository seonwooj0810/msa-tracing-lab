package lab.tracing.order;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class ProductClient {
    private final RestClient http;
    ProductClient(RestClient.Builder builder,
                  @Value("${clients.product.base-url}") String baseUrl,
                  @Value("${clients.connect-timeout}") Duration connectTimeout,
                  @Value("${clients.read-timeout}") Duration readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        // Keep Boot's ObservationRegistry and tracing customizations on the injected builder.
        this.http = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
    Product get(long productId) {
        Product product = http.get().uri("/products/{id}", productId).retrieve().body(Product.class);
        if (product == null) throw new RestClientException("Empty product response");
        return product;
    }
}
