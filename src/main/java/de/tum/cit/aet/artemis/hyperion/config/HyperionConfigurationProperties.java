package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration properties for Hyperion integration.
 *
 * Connection settings (host, port, TLS, timeouts, etc.) are now handled
 * by the gRPC Spring Boot starter through standard grpc.client.hyperion.* properties.
 *
 * This class now only contains application-specific business logic properties.
 */
@Configuration
@ConfigurationProperties(prefix = "artemis.hyperion")
@Lazy
public class HyperionConfigurationProperties {

    /**
     * Timeout for consistency check operations in seconds.
     * This is a business logic timeout, not a gRPC connection timeout.
     */
    private int consistencyCheckTimeoutSeconds = 300; // 5 minutes

    public int getConsistencyCheckTimeoutSeconds() {
        return consistencyCheckTimeoutSeconds;
    }

    public void setConsistencyCheckTimeoutSeconds(int consistencyCheckTimeoutSeconds) {
        this.consistencyCheckTimeoutSeconds = consistencyCheckTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "HyperionConfigurationProperties{" + "consistencyCheckTimeoutSeconds=" + consistencyCheckTimeoutSeconds + '}';
    }
}
