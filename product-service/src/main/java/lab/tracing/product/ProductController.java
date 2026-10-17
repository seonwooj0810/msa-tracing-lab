package lab.tracing.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ProductController {
    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    @GetMapping("/products/{id}")
    Product get(@PathVariable long id) {
        if (id != 100) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        log.info("Returning product productId={}", id);
        return new Product(100, "MSA 학습 도서", 30000);
    }
    record Product(long id, String name, long price) {}
}
