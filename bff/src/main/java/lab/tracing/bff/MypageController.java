package lab.tracing.bff;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MypageController {
    private final MypageService mypage;
    MypageController(MypageService mypage) { this.mypage = mypage; }
    @GetMapping("/api/mypage/{memberId}")
    MypageResponse get(@PathVariable long memberId) { return mypage.get(memberId); }
}

record MypageResponse(String traceId, Member member, List<Order> orders) {}
record Member(long id, String name) {}
record Order(long id, long memberId, Product product, int quantity) {}
record Product(long id, String name, long price) {}
