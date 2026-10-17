package lab.tracing.product.product.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lab.tracing.product.common.response.ApiResult;
import lab.tracing.product.product.application.ProductService;
import lombok.RequiredArgsConstructor;

@Tag(name = "01. 상품")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

	private final ProductService productService;

	@Operation(
		summary = "상품 상세 조회",
		description = "상품 식별자로 단건 상세 정보를 조회한다."
	)
	@GetMapping("/{productId}")
	public ApiResult<ProductDetailResponse> getProduct(
		@PathVariable long productId
	) {
		return ApiResult.success(productService.getProduct(productId));
	}
}
