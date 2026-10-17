package lab.tracing.bff.mypage.infrastructure.order;

public record OrderResponse(long id, long memberId, ProductResponse product, int quantity) {
}
