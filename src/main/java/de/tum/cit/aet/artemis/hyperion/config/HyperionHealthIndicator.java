package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;

/**
 * Health indicator for Hyperion gRPC service.
 *
 * This health indicator uses the standard gRPC health checking protocol to determine
 * if the Hyperion service is available and responding.
 */
@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HyperionHealthIndicator.class);

    /**
     * Service name for overall health check.
     * Empty string checks the overall server health according to gRPC health protocol.
     */
    private static final String OVERALL_SERVICE_NAME = "";

    private static final String HYPERION_URL_KEY = "url";

    private static final String HYPERION_SECURITY_KEY = "security";

    private static final String HYPERION_GRPC_STATUS_KEY = "grpcStatus";

    private final HealthGrpc.HealthBlockingStub healthStub;

    private final HyperionConfigurationProperties config;

    public HyperionHealthIndicator(@Qualifier("hyperionHealthStub") HealthGrpc.HealthBlockingStub healthStub, HyperionConfigurationProperties config) {
        this.healthStub = healthStub;
        this.config = config;
    }

    @Override
    public Health health() {
        var additionalInfo = new HashMap<String, Object>();
        additionalInfo.put(HYPERION_URL_KEY, config.getHost() + ":" + config.getPort());
        additionalInfo.put(HYPERION_SECURITY_KEY, getSecurityMode(config));

        ConnectorHealth health;
        try {
            // Create health check request for overall service health
            HealthCheckRequest request = HealthCheckRequest.newBuilder().setService(OVERALL_SERVICE_NAME).build();

            log.debug("Performing gRPC health check for Hyperion at {}:{}", config.getHost(), config.getPort());

            // Perform health check
            HealthCheckResponse response = healthStub.withDeadlineAfter(config.getTimeouts().getHealth().toMillis(), TimeUnit.MILLISECONDS).check(request);

            HealthCheckResponse.ServingStatus status = response.getStatus();
            additionalInfo.put(HYPERION_GRPC_STATUS_KEY, status.name());

            boolean isHealthy = status == HealthCheckResponse.ServingStatus.SERVING;
            health = new ConnectorHealth(isHealthy, additionalInfo);

            if (isHealthy) {
                log.debug("Hyperion health check successful: {}", status.name());
            }
            else {
                log.warn("Hyperion health check returned non-serving status: {}", status.name());
            }

        }
        catch (Exception ex) {
            log.warn("Hyperion gRPC health check failed", ex);
            health = new ConnectorHealth(false, additionalInfo, ex);
        }

        return health.asActuatorHealth();
    }

    /**
     * Determines the security mode based on configuration.
     *
     * @param config the Hyperion configuration
     * @return a descriptive string indicating the security mode
     */
    private static String getSecurityMode(HyperionConfigurationProperties config) {
        if (config.isMutualTlsEnabled()) {
            return "mTLS (mutual authentication)";
        }
        else if (config.isTlsEnabled()) {
            return "TLS (server authentication only)";
        }
        else {
            return "plaintext (⚠️ insecure)";
        }
    }
}
