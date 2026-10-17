package lab.tracing.member.common.error;

import static org.springframework.http.HttpStatus.*;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	MEMBER_NOT_FOUND(NOT_FOUND, 1001, "회원을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final int code;
	private final String message;
}
