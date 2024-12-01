package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import de.tum.cit.aet.artemis.core.web.CustomMetricsExtension;
import io.micrometer.core.annotation.Timed;
import tech.jhipster.config.metric.JHipsterMetricsEndpoint;

/**
 * CustomMetricsExtensionConfiguration.
 * Configuration for custom Artemis metrics.
 */
public class CustomMetricsExtensionConfiguration {

    @Profile(PROFILE_CORE)
    @Configuration
    @ConditionalOnClass(Timed.class)
    @AutoConfigureAfter(JHipsterMetricsEndpointConfiguration.class)
    public static class JHipsterMetricsEndpointConfiguration {

        /**
         * customMetricsExtension.
         *
         * @param jHipsterMetricsEndpoint Default JHI Metrics
         * @param simpUserRegistry        Registry used to retrieve the number of active users.
         * @return CustomMetricsExtension object.
         */
        @Bean
        @ConditionalOnBean({ JHipsterMetricsEndpoint.class, SimpUserRegistry.class })
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint
        public CustomMetricsExtension customMetricsExtension(JHipsterMetricsEndpoint jHipsterMetricsEndpoint, SimpUserRegistry simpUserRegistry) {
            return new CustomMetricsExtension(jHipsterMetricsEndpoint, simpUserRegistry);
        }
    }
}
