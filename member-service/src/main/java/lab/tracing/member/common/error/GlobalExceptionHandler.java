package lab.tracing.member.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;

@RestControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler {

	private final Tracer tracer;

	@ExceptionHandler(CustomException.class)
	ResponseEntity<ErrorResponse> handleCustomException(CustomException exception) {
		return ResponseEntity.status(exception.getErrorCode().getStatus())
			.body(ErrorResponse.of(exception.getErrorCode(), traceId()));
	}

	private String traceId() {
		var span = tracer.currentSpan();
		return span == null ? null : span.context().traceId();
	}
}
