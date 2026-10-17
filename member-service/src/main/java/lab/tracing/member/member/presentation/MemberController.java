package lab.tracing.member.member.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lab.tracing.member.common.response.ApiResult;
import lab.tracing.member.member.application.MemberService;
import lombok.RequiredArgsConstructor;

@Tag(name = "01. 회원")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

	private final MemberService memberService;

	@Operation(
		summary = "회원 상세 조회",
		description = "회원 식별자로 단건 상세 정보를 조회한다."
	)
	@GetMapping("/{memberId}")
	public ApiResult<MemberDetailResponse> getMember(
		@PathVariable long memberId
	) {
		return ApiResult.success(memberService.getMember(memberId));
	}
}
