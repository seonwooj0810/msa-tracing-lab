package lab.tracing.bff.mypage.infrastructure.order;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

import lab.tracing.bff.common.response.ApiResult;

public interface OrderClient {

	@GetExchange("/api/v1/orders")
	ApiResult<List<OrderResponse>> getOrders(@RequestParam long memberId);
}
