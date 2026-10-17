package lab.tracing.product.product.application;

import org.springframework.stereotype.Service;

import lab.tracing.product.common.error.CustomException;
import lab.tracing.product.common.error.ErrorCode;
import lab.tracing.product.product.domain.Product;
import lab.tracing.product.product.presentation.ProductDetailResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProductService {

	public ProductDetailResponse getProduct(long productId) {
		if (productId != 100) {
			throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
		}
		log.info("Returning product productId={}", productId);
		return ProductDetailResponse.from(new Product(100, "MSA 학습 도서", 30000));
	}
}
