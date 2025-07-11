package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration properties for Hyperion integration.
 *
 * <p>
 * These properties can be configured via application.yml under the prefix {@code artemis.hyperion}.
 * All timeout configurations support standard Spring Boot duration formats (e.g., "5m", "120s", "PT2M").
 *
 * <p>
 * Example configuration:
 *
 * <pre>
 * artemis:
 *   hyperion:
 *     host: hyperion-service.local
 *     port: 50051
 *     root-ca: /etc/certs/ca.pem
 *     client-cert: /etc/certs/client.pem
 *     client-key: /etc/certs/client.key
 *     timeouts:
 *       health: 5s
 *       consistency-check: 5m
 *       rewrite-problem-statement: 2m
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "artemis.hyperion")
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionConfigurationProperties {

    /**
     * DNS-name or IP of the Hyperion gRPC service
     * Default: localhost
     */
    private String host = "localhost";

    /**
     * Port of the Hyperion gRPC service
     * Default: 50051
     */
    private int port = 50051;

    /**
     * mTLS PEM files for secure gRPC connection
     */
    private Path rootCa;

    private Path clientCert;

    private Path clientKey;

    /**
     * Timeout configurations for various Hyperion operations.
     */
    private final Timeouts timeouts = new Timeouts();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Path getRootCa() {
        return rootCa;
    }

    public void setRootCa(Path rootCa) {
        this.rootCa = rootCa;
    }

    public Path getClientCert() {
        return clientCert;
    }

    public void setClientCert(Path clientCert) {
        this.clientCert = clientCert;
    }

    public Path getClientKey() {
        return clientKey;
    }

    public void setClientKey(Path clientKey) {
        this.clientKey = clientKey;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    /**
     * Timeout configurations for Hyperion service operations.
     * These are business logic timeouts, not gRPC connection timeouts.
     */
    public static class Timeouts {

        /**
         * Timeout for health check operations.
         * Default: 5 seconds
         */
        private Duration health = Duration.ofSeconds(5);

        /**
         * Timeout for consistency check operations.
         * Default: 5 minutes
         */
        private Duration consistencyCheck = Duration.ofMinutes(5);

        /**
         * Timeout for problem statement rewriting operations.
         * Default: 2 minutes
         */
        private Duration rewriteProblemStatement = Duration.ofMinutes(2);

        public Duration getHealth() {
            return health;
        }

        public void setHealth(Duration health) {
            this.health = health;
        }

        public Duration getConsistencyCheck() {
            return consistencyCheck;
        }

        public void setConsistencyCheck(Duration consistencyCheck) {
            this.consistencyCheck = consistencyCheck;
        }

        public Duration getRewriteProblemStatement() {
            return rewriteProblemStatement;
        }

        public void setRewriteProblemStatement(Duration rewriteProblemStatement) {
            this.rewriteProblemStatement = rewriteProblemStatement;
        }

        @Override
        public String toString() {
            return "Timeouts{" + "health=" + health + ", consistencyCheck=" + consistencyCheck + ", rewriteProblemStatement=" + rewriteProblemStatement + '}';
        }
    }

    /**
     * Checks if TLS is enabled for the gRPC connection.
     * TLS is considered enabled if at least a root CA certificate is provided.
     *
     * @return true if TLS is enabled, false otherwise
     */
    public boolean isTlsEnabled() {
        return rootCa != null;
    }

    /**
     * Checks if mutual TLS (mTLS) is enabled for the gRPC connection.
     * mTLS requires both a client certificate and private key in addition to the root CA.
     *
     * @return true if mTLS is enabled, false otherwise
     */
    public boolean isMutualTlsEnabled() {
        return isTlsEnabled() && clientCert != null && clientKey != null;
    }

    @Override
    public String toString() {
        return "HyperionConfigurationProperties{" + "host='" + host + '\'' + ", port=" + port + ", rootCa=" + rootCa + ", timeouts=" + timeouts + '}';
    }
}
