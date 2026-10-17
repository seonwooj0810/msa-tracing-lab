package lab.tracing.member.common.response;

public record ApiResult<T>(
	boolean success,
	T data
) {

	public static <T> ApiResult<T> success(T data) {
		return new ApiResult<>(true, data);
	}
}
