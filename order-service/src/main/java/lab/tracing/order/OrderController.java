package lab.tracing.order;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {
    private final OrderService orders;
    OrderController(OrderService orders) { this.orders = orders; }
    @GetMapping("/orders")
    List<Order> find(@RequestParam long memberId) { return orders.find(memberId); }
}

record Order(long id, long memberId, Product product, int quantity) {}
record Product(long id, String name, long price) {}
