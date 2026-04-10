package de.tum.cit.aet.artemis.iris.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.lecture.api.ProcessingStateCallbackApi;

@Lazy
@Configuration
@Conditional(IrisEnabled.class)
public class PyrisConfiguration {

    /**
     * Creates the Pyris health indicator bean with deferred resolution of {@link ProcessingStateCallbackApi}.
     * Using {@link ObjectProvider} prevents the entire processing-state callback dependency chain
     * from being eagerly instantiated at startup, reducing the startup bean dependency edge count.
     *
     * @param restTemplate               the short-timeout REST template for Pyris health checks
     * @param processingStateCallbackApi lazily resolved callback API for handling Iris restarts
     * @return the configured health indicator
     */
    @Bean
    @Lazy
    public PyrisHealthIndicator pyrisHealthIndicator(@Qualifier("shortTimeoutPyrisRestTemplate") RestTemplate restTemplate,
            ObjectProvider<ProcessingStateCallbackApi> processingStateCallbackApi) {
        return new PyrisHealthIndicator(restTemplate, processingStateCallbackApi);
    }
}
