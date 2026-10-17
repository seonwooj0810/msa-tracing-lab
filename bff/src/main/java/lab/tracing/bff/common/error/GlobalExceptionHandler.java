package lab.tracing.bff.common.error;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
class GlobalExceptionHandler {

	private final Tracer tracer;

	@ExceptionHandler(CustomException.class)
	ResponseEntity<ErrorResponse> handleCustomException(CustomException exception) {
		return ResponseEntity.status(exception.getErrorCode().getStatus())
			.body(ErrorResponse.of(exception.getErrorCode(), traceId()));
	}

	/** 하위 서비스의 4xx는 status와 body를 그대로 전달한다. */
	@ExceptionHandler(HttpClientErrorException.class)
	ResponseEntity<byte[]> handleDownstreamClientError(HttpClientErrorException exception) {
		MediaType contentType = Optional.ofNullable(exception.getResponseHeaders())
			.map(HttpHeaders::getContentType)
			.orElse(MediaType.APPLICATION_JSON);
		return ResponseEntity.status(exception.getStatusCode())
			.contentType(contentType)
			.body(exception.getResponseBodyAsByteArray());
	}

	/** 하위 서비스의 5xx와 연결 실패·타임아웃은 DOWNSTREAM_ERROR로 변환한다. */
	@ExceptionHandler(RestClientException.class)
	ResponseEntity<ErrorResponse> handleDownstreamFailure(RestClientException exception) {
		log.warn("Downstream request failed", exception);
		return handleCustomException(new CustomException(ErrorCode.DOWNSTREAM_ERROR));
	}

	private String traceId() {
		var span = tracer.currentSpan();
		return span == null ? null : span.context().traceId();
	}
}
