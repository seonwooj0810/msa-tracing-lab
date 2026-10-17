package lab.tracing.order.order.presentation;

import jakarta.validation.constraints.NotNull;

public record OrderSearchRequest(@NotNull Long memberId) {
}
