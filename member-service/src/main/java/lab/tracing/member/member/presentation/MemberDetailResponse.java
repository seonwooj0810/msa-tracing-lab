package lab.tracing.member.member.presentation;

import lab.tracing.member.member.domain.Member;

public record MemberDetailResponse(long id, String name) {

	public static MemberDetailResponse from(Member member) {
		return new MemberDetailResponse(member.id(), member.name());
	}
}
