package lab.tracing.bff;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class MypageService {
    private static final Logger log = LoggerFactory.getLogger(MypageService.class);
    private final DownstreamClients clients;
    private final Tracer tracer;
    MypageService(DownstreamClients clients, Tracer tracer) { this.clients = clients; this.tracer = tracer; }
    MypageResponse get(long memberId) {
        if (memberId != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        log.info("Calling member-service memberId={}", memberId);
        Member member = clients.member(memberId);
        log.info("Completed member-service memberId={}", memberId);
        // This blocking call starts only after the member response has been consumed.
        log.info("Calling order-service memberId={}", memberId);
        var orders = clients.orders(memberId);
        log.info("Completed order-service memberId={}", memberId);
        return new MypageResponse(tracer.currentSpan().context().traceId(), member, orders);
    }
}
