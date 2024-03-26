package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.TextOutputFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.MetricsBean;
import io.prometheus.client.CollectorRegistry;

/**
 * CustomMetricsPrometheusExtension.
 * Extends the default JHI Metrics with custom metrics for Artemis when using Prometheus.
 */
@Component
@Profile(PROFILE_CORE)
@WebEndpoint(id = "prometheus")
public class CustomPrometheusMetricsExtension extends PrometheusScrapeEndpoint {

    private final Optional<CollectorRegistry> optionalCollectorRegistry;

    private final MetricsBean metricsBean;

    public CustomPrometheusMetricsExtension(Optional<CollectorRegistry> optionalCollectorRegistry, MetricsBean metricsBean) {
        // If Prometheus is disabled, the optionalCollectorRegistry is empty, and we return an empty WebEndpointResponse in scrape().
        // Thus, we can safely initialise the super class with null, as we will never use it when Prometheus is disabled.
        super(optionalCollectorRegistry.orElse(null));
        this.optionalCollectorRegistry = optionalCollectorRegistry;
        this.metricsBean = metricsBean;
    }

    /**
     * Expands the Prometheus metrics call with custom metrics.
     *
     * @param format        the format for the response
     * @param includedNames names of the samples included in the result
     * @return WebEndpointResponse with current metrics
     */
    @ReadOperation(producesFrom = TextOutputFormat.class)
    public WebEndpointResponse<String> scrape(TextOutputFormat format, @Nullable Set<String> includedNames) {
        if (optionalCollectorRegistry.isEmpty()) {
            return new WebEndpointResponse<>();
        }

        metricsBean.recalculateLiveMetrics();
        return super.scrape(format, includedNames);
    }

}
