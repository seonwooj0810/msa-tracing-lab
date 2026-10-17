package lab.tracing.product.common.error;

public record ErrorResponse(
	int code,
	String message,
	String traceId
) {

	public static ErrorResponse of(ErrorCode errorCode, String traceId) {
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), traceId);
	}
}
