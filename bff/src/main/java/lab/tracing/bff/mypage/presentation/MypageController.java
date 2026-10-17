package lab.tracing.bff.mypage.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lab.tracing.bff.common.response.ApiResult;
import lab.tracing.bff.mypage.application.MypageFacadeService;
import lombok.RequiredArgsConstructor;

@Tag(name = "01. 마이페이지")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/{memberId}/mypage")
public class MypageController {

	private final MypageFacadeService mypageFacadeService;

	@Operation(
		summary = "마이페이지 조회",
		description = "회원 정보를 조회한 뒤 주문 목록을 순차로 조회해 한 번에 반환한다."
	)
	@GetMapping
	public ApiResult<MypageDetailResponse> getMypage(
		@PathVariable long memberId
	) {
		return ApiResult.success(mypageFacadeService.getMypage(memberId));
	}
}
