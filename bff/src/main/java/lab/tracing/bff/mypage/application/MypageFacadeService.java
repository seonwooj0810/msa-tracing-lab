package lab.tracing.bff.mypage.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.micrometer.tracing.Tracer;
import lab.tracing.bff.common.error.CustomException;
import lab.tracing.bff.common.error.ErrorCode;
import lab.tracing.bff.common.response.ApiResult;
import lab.tracing.bff.mypage.infrastructure.member.MemberClient;
import lab.tracing.bff.mypage.infrastructure.member.MemberResponse;
import lab.tracing.bff.mypage.infrastructure.order.OrderClient;
import lab.tracing.bff.mypage.infrastructure.order.OrderResponse;
import lab.tracing.bff.mypage.presentation.MypageDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MypageFacadeService {

	private final MemberClient memberClient;
	private final OrderClient orderClient;
	private final Tracer tracer;

	/** 이 실습은 member → order 순차 호출 자체가 목적이라 병렬화하지 않는다. */
	public MypageDetailResponse getMypage(long memberId) {
		if (memberId != 1) {
			throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
		}
		log.info("Calling member-service memberId={}", memberId);
		MemberResponse member = unwrap(memberClient.getMember(memberId));
		log.info("Completed member-service memberId={}", memberId);
		// This blocking call starts only after the member response has been consumed.
		log.info("Calling order-service memberId={}", memberId);
		List<OrderResponse> orders = unwrap(orderClient.getOrders(memberId));
		log.info("Completed order-service memberId={}", memberId);
		return MypageDetailResponse.from(tracer.currentSpan().context().traceId(), member, orders);
	}

	private <T> T unwrap(ApiResult<T> result) {
		return Optional.ofNullable(result)
			.map(ApiResult::data)
			.orElseThrow(() -> new CustomException(ErrorCode.DOWNSTREAM_ERROR));
	}
}
