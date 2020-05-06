package de.tum.in.www1.artemis.config;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import de.tum.in.www1.artemis.web.rest.CustomMetricsExtension;
import io.github.jhipster.config.metric.JHipsterMetricsEndpoint;
import io.micrometer.core.annotation.Timed;

public class CustomMetricsExtensionConfiguration {

    @Configuration
    @ConditionalOnClass(Timed.class)
    @AutoConfigureAfter(JHipsterMetricsEndpointConfiguration.class)
    public class JHipsterMetricsEndpointConfiguration {

        @Bean
        @ConditionalOnBean({ JHipsterMetricsEndpoint.class, SimpUserRegistry.class })
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint
        public CustomMetricsExtension customMetricsExtension(JHipsterMetricsEndpoint jHipsterMetricsEndpoint, SimpUserRegistry simpUserRegistry) {
            return new CustomMetricsExtension(jHipsterMetricsEndpoint, simpUserRegistry);
        }
    }
}
