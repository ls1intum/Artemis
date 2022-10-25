package de.tum.in.www1.artemis.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MetricsBean {

    private final Logger log = LoggerFactory.getLogger(MetricsBean.class);

    private static final String ARTEMIS_HEALTH_NAME = "artemis.health";

    private static final String ARTEMIS_HEALTH_DESCRIPTION = "Artemis Health Indicator";

    private static final String ARTEMIS_HEALTH_TAG = "healthindicator";

    private static final int LOGGING_DELAY_SECONDS = 10;

    private final MeterRegistry meterRegistry;

    private final Environment env;

    private final TaskScheduler taskScheduler;

    private final WebSocketMessageBrokerStats webSocketStats;

    private final SimpUserRegistry userRegistry;

    private final WebSocketHandler webSocketHandler;

    public MetricsBean(MeterRegistry meterRegistry, Environment env, TaskScheduler taskScheduler, WebSocketMessageBrokerStats webSocketStats, SimpUserRegistry userRegistry,
            WebSocketHandler websocketHandler, List<HealthContributor> healthContributors, Optional<HikariDataSource> hikariDataSource) {
        this.meterRegistry = meterRegistry;
        this.env = env;
        this.taskScheduler = taskScheduler;
        this.webSocketStats = webSocketStats;
        this.userRegistry = userRegistry;
        this.webSocketHandler = websocketHandler;

        registerHealthContributors(healthContributors);
        registerWebsocketMetrics();
        // the data source is optional as it is not used during testing
        hikariDataSource.ifPresent(this::registerDatasourceMetrics);
    }

    /**
     * initialize the websocket logging
     */
    @PostConstruct
    public void init() {
        // using Autowired leads to a weird bug, because the order of the method execution is changed. This somehow prevents messages send to single clients
        // later one, e.g. in the code editor. Therefore, we call this method here directly to get a reference and adapt the logging period!
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        // Note: this mechanism prevents that this is logged during testing
        if (activeProfiles.contains("websocketLog")) {
            webSocketStats.setLoggingPeriod(LOGGING_DELAY_SECONDS * 1000L);
            taskScheduler.scheduleAtFixedRate(() -> {
                final var connectedUsers = userRegistry.getUsers();
                final var subscriptionCount = connectedUsers.stream().flatMap(simpUser -> simpUser.getSessions().stream()).map(simpSession -> simpSession.getSubscriptions().size())
                        .reduce(0, Integer::sum);
                log.info("Currently connect users {} with active websocket subscriptions: {}", connectedUsers.size(), subscriptionCount);
            }, LOGGING_DELAY_SECONDS * 1000L);
        }
    }

    private void registerHealthContributors(List<HealthContributor> healthContributors) {
        // Publish the health status for each HealthContributor one Gauge with name ARTEMIS_HEALTH_NAME that has several values (one for each HealthIndicator),
        // using different values for the ARTEMIS_HEALTH_TAG tag
        for (HealthContributor healthContributor : healthContributors) {
            // For most HealthContributors, there is only one HealthIndicator that can directly be published.
            // The health status gets mapped to a double value, as only doubles can be returned by a Gauge.
            if (healthContributor instanceof HealthIndicator healthIndicator) {
                Gauge.builder(ARTEMIS_HEALTH_NAME, healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description(ARTEMIS_HEALTH_DESCRIPTION)
                        .tag(ARTEMIS_HEALTH_TAG, healthIndicator.getClass().getSimpleName().toLowerCase()).register(meterRegistry);
            }

            // The DiscoveryCompositeHealthContributor can consist of several HealthIndicators, so they must all be published
            if (healthContributor instanceof DiscoveryCompositeHealthContributor discoveryCompositeHealthContributor) {
                for (NamedContributor<HealthContributor> discoveryHealthContributor : discoveryCompositeHealthContributor) {
                    if (discoveryHealthContributor.getContributor() instanceof HealthIndicator healthIndicator) {
                        Gauge.builder(ARTEMIS_HEALTH_NAME, healthIndicator, h -> mapHealthToDouble(h.health())).strongReference(true).description(ARTEMIS_HEALTH_DESCRIPTION)
                                .tag(ARTEMIS_HEALTH_TAG, discoveryHealthContributor.getName().toLowerCase()).register(meterRegistry);
                    }
                }
            }
        }
    }

    private void registerWebsocketMetrics() {
        // Publish the number of currently (via WebSockets) connected users
        Gauge.builder("artemis.instance.websocket.users", webSocketHandler, MetricsBean::extractWebsocketUserCount).strongReference(true)
                .description("Number of users connected to this Artemis instance").register(meterRegistry);
    }

    private static double extractWebsocketUserCount(WebSocketHandler webSocketHandler) {
        if (webSocketHandler instanceof SubProtocolWebSocketHandler subProtocolWebSocketHandler) {
            return subProtocolWebSocketHandler.getStats().getWebSocketSessions();
        }
        return -1;
    }

    private void registerDatasourceMetrics(HikariDataSource dataSource) {
        dataSource.setMetricRegistry(meterRegistry);
    }

    /**
     * Maps the health status to a double
     *
     * @param health the Health whose status should be mapped
     * @return a double corresponding to the health status
     */
    private double mapHealthToDouble(Health health) {
        return switch (health.getStatus().getCode()) {
            case "UP" -> 1;
            case "DOWN" -> 0;
            case "OUT_OF_SERVICE" -> -1;
            case "UNKNOWN" -> -2;
            default -> -3;
        };
    }
}
