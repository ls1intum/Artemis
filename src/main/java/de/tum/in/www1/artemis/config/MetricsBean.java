package de.tum.in.www1.artemis.config;

import java.util.List;

import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import de.tum.in.www1.artemis.config.websocket.WebsocketConfiguration;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MetricsBean {

    private final String ARTEMIS_HEALTH_NAME = "artemis.health";

    private final String ARTEMIS_HEALTH_DESCRIPTION = "Artemis Health Indicator";

    private final String ARTEMIS_HEALTH_TAG = "healthindicator";

    public MetricsBean(MeterRegistry meterRegistry, WebsocketConfiguration websocketConfiguration, List<HealthContributor> healthContributors) {
        // Publish the number of currently (via WebSockets) connected users
        Gauge.builder("artemis.instance.websocket.users", websocketConfiguration.subProtocolWebSocketHandler(), MetricsBean::extractWebsocketUserCount).strongReference(true)
                .description("Number of users connected to this Artemis instance").register(meterRegistry);

        // Publish the health status for each HealthContributor
        // The health status gets published as one Gauge with name ARTEMIS_HEALTH_NAME that has several values (one for each HealthIndicator), using different values for the
        // ARTEMIS_HEALTH_TAG tag
        for (HealthContributor healthContributor : healthContributors) {
            // For most HealthContributors, there is only one HealthIndicator that can directly be published.
            // The health status gets mapped to a double value, as only doubles can be returned by a Gauge.
            if (healthContributor instanceof HealthIndicator) {
                HealthIndicator healthIndicator = (HealthIndicator) healthContributor;
                Gauge.builder(ARTEMIS_HEALTH_NAME, healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description(ARTEMIS_HEALTH_DESCRIPTION)
                        .tag(ARTEMIS_HEALTH_TAG, healthIndicator.getClass().getSimpleName().toLowerCase()).register(meterRegistry);
            }

            // The DiscoveryCompositeHealthContributor can consist of several HealthIndicators, so they must all be published
            if (healthContributor instanceof DiscoveryCompositeHealthContributor) {
                DiscoveryCompositeHealthContributor discoveryCompositeHealthContributor = (DiscoveryCompositeHealthContributor) healthContributor;
                for (NamedContributor<HealthContributor> discoveryHealthContributor : discoveryCompositeHealthContributor) {
                    if (discoveryHealthContributor.getContributor() instanceof HealthIndicator) {
                        HealthIndicator healthIndicator = (HealthIndicator) discoveryHealthContributor.getContributor();
                        Gauge.builder(ARTEMIS_HEALTH_NAME, healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description(ARTEMIS_HEALTH_DESCRIPTION)
                                .tag(ARTEMIS_HEALTH_TAG, discoveryHealthContributor.getName().toLowerCase()).register(meterRegistry);
                    }
                }
            }

        }
    }

    private static double extractWebsocketUserCount(WebSocketHandler webSocketHandler) {
        if (webSocketHandler instanceof SubProtocolWebSocketHandler) {
            SubProtocolWebSocketHandler subProtocolWebSocketHandler = (SubProtocolWebSocketHandler) webSocketHandler;
            return subProtocolWebSocketHandler.getStats().getWebSocketSessions();
        }
        return -1;
    }

    /**
     * Maps the health status to a double
     *
     * @param health the Health whose status should be mapped
     * @return a double corresponding to the health status
     */
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
