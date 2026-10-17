package lab.tracing.product.product.presentation;

import lab.tracing.product.product.domain.Product;

public record ProductDetailResponse(long id, String name, long price) {

	public static ProductDetailResponse from(Product product) {
		return new ProductDetailResponse(product.id(), product.name(), product.price());
	}
}
