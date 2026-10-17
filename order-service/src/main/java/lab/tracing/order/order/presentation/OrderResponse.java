package lab.tracing.order.order.presentation;

import lab.tracing.order.order.domain.Order;
import lab.tracing.order.order.infrastructure.product.ProductResponse;

public record OrderResponse(long id, long memberId, ProductResponse product, int quantity) {

	public static OrderResponse from(Order order, ProductResponse product) {
		return new OrderResponse(order.id(), order.memberId(), product, order.quantity());
	}
}
