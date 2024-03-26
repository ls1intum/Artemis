package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import javax.annotation.Nullable;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
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
public class CustomPrometheusMetricsExtension extends PrometheusScrapeEndpoint {

    private final MetricsBean metricsBean;

    public CustomPrometheusMetricsExtension(CollectorRegistry collectorRegistry, MetricsBean metricsBean) {
        super(collectorRegistry);
        this.metricsBean = metricsBean;
    }

    /**
     * Expands the Prometheus metrics call with custom metrics.
     *
     * @return WebEndpointResponse with current metrics
     */
    @ReadOperation(producesFrom = TextOutputFormat.class)
    public WebEndpointResponse<String> scrape(TextOutputFormat format, @Nullable Set<String> includedNames) {
        metricsBean.recalculateLiveMetrics();
        return super.scrape(format, includedNames);
    }

}
