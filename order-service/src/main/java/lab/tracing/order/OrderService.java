package lab.tracing.order;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final ProductClient products;
    OrderService(ProductClient products) { this.products = products; }
    List<Order> find(long memberId) {
        if (memberId != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        log.info("Calling product-service productId=100");
        Product product = products.get(100);
        log.info("Completed product-service productId=100");
        return List.of(new Order(1000, memberId, product, 1));
    }
}
