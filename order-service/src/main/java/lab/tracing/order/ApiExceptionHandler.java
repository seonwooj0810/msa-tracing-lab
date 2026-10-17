package lab.tracing.order;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final Tracer tracer;
    ApiExceptionHandler(Tracer tracer) { this.tracer = tracer; }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
            .body(new ApiError(traceId(), exception.getReason()));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiError> downstream(RestClientException exception) {
        log.warn("Downstream request failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError(traceId(), "Downstream request failed"));
    }

    private String traceId() {
        var span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    record ApiError(String traceId, String message) {}
}
