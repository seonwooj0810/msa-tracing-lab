package lab.tracing.order.order.domain;

public record Order(long id, long memberId, long productId, int quantity) {
}
