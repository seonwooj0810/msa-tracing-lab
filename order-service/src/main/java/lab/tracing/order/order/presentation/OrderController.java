package lab.tracing.order.order.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lab.tracing.order.common.response.ApiResult;
import lab.tracing.order.order.application.OrderService;
import lombok.RequiredArgsConstructor;

@Tag(name = "01. 주문")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

	private final OrderService orderService;

	@Operation(
		summary = "주문 목록 조회",
		description = "회원 식별자로 주문 목록을 조회하고 상품 서비스에서 상품 정보를 채운다."
	)
	@GetMapping
	public ApiResult<List<OrderResponse>> getOrders(
		@Valid @ModelAttribute OrderSearchRequest request
	) {
		return ApiResult.success(orderService.getOrders(request));
	}
}
