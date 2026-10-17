package lab.tracing.bff.mypage.infrastructure.member;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

import lab.tracing.bff.common.response.ApiResult;

public interface MemberClient {

	@GetExchange("/api/v1/members/{memberId}")
	ApiResult<MemberResponse> getMember(@PathVariable long memberId);
}
