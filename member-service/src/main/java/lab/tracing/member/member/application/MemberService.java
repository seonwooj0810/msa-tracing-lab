package lab.tracing.member.member.application;

import org.springframework.stereotype.Service;

import lab.tracing.member.common.error.CustomException;
import lab.tracing.member.common.error.ErrorCode;
import lab.tracing.member.member.domain.Member;
import lab.tracing.member.member.presentation.MemberDetailResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MemberService {

	public MemberDetailResponse getMember(long memberId) {
		if (memberId != 1) {
			throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
		}
		log.info("Returning member memberId={}", memberId);
		return MemberDetailResponse.from(new Member(1, "선우"));
	}
}
