package de.tum.in.www1.artemis.config;

import java.util.List;

import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MetricsBean {

    public MetricsBean(MeterRegistry meterRegistry, SimpUserRegistry simpUserRegistry, List<HealthContributor> healthContributors) {
        Gauge.builder("artemis.instance.websocket.users", simpUserRegistry, SimpUserRegistry::getUserCount).strongReference(true)
                .description("Number of users connected to this Artemis instance").register(meterRegistry);

        for (HealthContributor healthContributor : healthContributors) {
            if (healthContributor instanceof HealthIndicator) {
                HealthIndicator healthIndicator = (HealthIndicator) healthContributor;
                Gauge.builder("artemis.health", healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description("Artemis Health Indicator")
                        .tag("healtindicator", healthIndicator.getClass().getSimpleName().toLowerCase()).register(meterRegistry);
            }
            if (healthContributor instanceof DiscoveryCompositeHealthContributor) {
                DiscoveryCompositeHealthContributor discoveryCompositeHealthContributor = (DiscoveryCompositeHealthContributor) healthContributor;
                for (NamedContributor<HealthContributor> discoveryHealthContributor : discoveryCompositeHealthContributor) {
                    if (discoveryHealthContributor.getContributor() instanceof HealthIndicator) {
                        HealthIndicator healthIndicator = (HealthIndicator) discoveryHealthContributor.getContributor();
                        Gauge.builder("artemis.health", healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description("Artemis Health Indicator")
                                .tag("healtindicator", discoveryHealthContributor.getName().toLowerCase()).register(meterRegistry);
                    }
                }
            }

        }
    }

    private double mapHealthToDouble(Health health) {
        switch (health.getStatus().getCode()) {
            case "UP":
                return 1;
            case "DOWN":
                return 0;
            case "OUT_OF_SERVICE":
                return -1;
            case "UNKNOWN":
                return -2;
            default:
                return -3;
        }
    }
}
