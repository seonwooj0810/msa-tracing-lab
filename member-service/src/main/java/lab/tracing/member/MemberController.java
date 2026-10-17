package lab.tracing.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class MemberController {
    private static final Logger log = LoggerFactory.getLogger(MemberController.class);
    @GetMapping("/members/{id}")
    Member get(@PathVariable long id) {
        if (id != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        log.info("Returning member memberId={}", id);
        return new Member(1, "선우");
    }
    record Member(long id, String name) {}
}
