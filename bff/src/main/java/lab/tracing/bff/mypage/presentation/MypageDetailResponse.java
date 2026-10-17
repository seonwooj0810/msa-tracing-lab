package lab.tracing.bff.mypage.presentation;

import java.util.List;

import lab.tracing.bff.mypage.infrastructure.member.MemberResponse;
import lab.tracing.bff.mypage.infrastructure.order.OrderResponse;

public record MypageDetailResponse(String traceId, MemberResponse member, List<OrderResponse> orders) {

	public static MypageDetailResponse from(String traceId, MemberResponse member, List<OrderResponse> orders) {
		return new MypageDetailResponse(traceId, member, orders);
	}
}
