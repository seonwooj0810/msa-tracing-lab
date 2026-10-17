package lab.tracing.order.order.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lab.tracing.order.common.error.CustomException;
import lab.tracing.order.common.error.ErrorCode;
import lab.tracing.order.common.response.ApiResult;
import lab.tracing.order.order.domain.Order;
import lab.tracing.order.order.infrastructure.product.ProductClient;
import lab.tracing.order.order.infrastructure.product.ProductResponse;
import lab.tracing.order.order.presentation.OrderResponse;
import lab.tracing.order.order.presentation.OrderSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

	private final ProductClient productClient;

	public List<OrderResponse> getOrders(OrderSearchRequest request) {
		if (request.memberId() != 1) {
			throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
		}
		Order order = new Order(1000, request.memberId(), 100, 1);
		log.info("Calling product-service productId={}", order.productId());
		ProductResponse product = unwrap(productClient.getProduct(order.productId()));
		log.info("Completed product-service productId={}", order.productId());
		return List.of(OrderResponse.from(order, product));
	}

	private <T> T unwrap(ApiResult<T> result) {
		return Optional.ofNullable(result)
			.map(ApiResult::data)
			.orElseThrow(() -> new CustomException(ErrorCode.DOWNSTREAM_ERROR));
	}
}
