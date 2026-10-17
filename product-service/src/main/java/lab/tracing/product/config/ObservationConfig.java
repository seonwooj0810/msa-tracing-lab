package lab.tracing.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;

@Configuration
class ObservationConfig {

	/** 헬스체크·메트릭 수집 요청이 trace를 뒤덮지 않도록 actuator 요청은 관측하지 않는다. */
	@Bean
	ObservationPredicate actuatorObservationPredicate() {
		return (name, context) -> !(context instanceof ServerRequestObservationContext serverContext
			&& serverContext.getCarrier().getRequestURI().startsWith("/actuator"));
	}
}
