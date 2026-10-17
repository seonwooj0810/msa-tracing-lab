package lab.tracing.order.order.infrastructure.product;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

import lab.tracing.order.common.response.ApiResult;

public interface ProductClient {

	@GetExchange("/api/v1/products/{productId}")
	ApiResult<ProductResponse> getProduct(@PathVariable long productId);
}
