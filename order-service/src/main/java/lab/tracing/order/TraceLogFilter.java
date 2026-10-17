package lab.tracing.order;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class TraceLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TraceLogFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        // ServerHttpObservationFilter has already established the current server span.
        log.debug("Incoming {} {} traceparent={}", request.getMethod(),
            request.getRequestURI(), request.getHeader("traceparent"));
        chain.doFilter(request, response);
    }
}
