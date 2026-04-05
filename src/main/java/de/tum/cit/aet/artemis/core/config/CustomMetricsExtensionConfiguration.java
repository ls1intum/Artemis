package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import de.tum.cit.aet.artemis.core.config.metric.ArtemisMetricsEndpoint;
import de.tum.cit.aet.artemis.core.config.metric.NodeMetricsCollector;
import de.tum.cit.aet.artemis.core.web.CustomMetricsExtension;
import io.micrometer.core.annotation.Timed;

/**
 * CustomMetricsExtensionConfiguration.
 * Configuration for custom Artemis metrics.
 */
public class CustomMetricsExtensionConfiguration {

    @Profile(PROFILE_CORE)
    @Configuration
    @Lazy
    @ConditionalOnClass(Timed.class)
    @AutoConfigureAfter(ArtemisMetricsEndpointConfiguration.class)
    public static class ArtemisMetricsEndpointConfiguration {

        /**
         * Creates the CustomMetricsExtension that adds multi-node aggregation and active user counts.
         *
         * @param nodeMetricsCollector collector for multi-node metrics aggregation
         * @param simpUserRegistry     registry used to retrieve the number of active WebSocket users
         * @return CustomMetricsExtension object
         */
        @Bean
        @Lazy
        @ConditionalOnBean({ ArtemisMetricsEndpoint.class, NodeMetricsCollector.class, SimpUserRegistry.class })
        @ConditionalOnMissingBean
        @ConditionalOnAvailableEndpoint
        public CustomMetricsExtension customMetricsExtension(NodeMetricsCollector nodeMetricsCollector, SimpUserRegistry simpUserRegistry) {
            return new CustomMetricsExtension(nodeMetricsCollector, simpUserRegistry);
        }
    }
}
