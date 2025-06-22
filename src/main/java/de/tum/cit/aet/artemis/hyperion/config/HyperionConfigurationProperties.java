package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration properties for Hyperion gRPC client.
 */
@Configuration
@ConfigurationProperties(prefix = "artemis.hyperion")
@Lazy
public class HyperionConfigurationProperties {

    private String host = "localhost";

    private int port = 50051;

    private boolean useTls = false;

    // mTLS client certificate authentication - required when TLS is enabled
    private String clientCertPath = "";

    private String clientKeyPath = "";

    private String serverCaPath = "";

    private int defaultTimeoutSeconds = 60;

    private int consistencyCheckTimeoutSeconds = 300;

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

    public boolean isUseTls() {
        return useTls;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }

    public String getServerCaPath() {
        return serverCaPath;
    }

    public void setServerCaPath(String serverCaPath) {
        this.serverCaPath = serverCaPath;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getConsistencyCheckTimeoutSeconds() {
        return consistencyCheckTimeoutSeconds;
    }

    public void setConsistencyCheckTimeoutSeconds(int consistencyCheckTimeoutSeconds) {
        this.consistencyCheckTimeoutSeconds = consistencyCheckTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "HyperionConfigurationProperties{" + "host='" + host + '\'' + ", port=" + port + ", useTls=" + useTls + ", clientCertPath='"
                + (clientCertPath != null && !clientCertPath.isEmpty() ? "[CONFIGURED]" : "not set") + '\'' + ", clientKeyPath='"
                + (clientKeyPath != null && !clientKeyPath.isEmpty() ? "[CONFIGURED]" : "not set") + '\'' + ", serverCaPath='"
                + (serverCaPath != null && !serverCaPath.isEmpty() ? "[CONFIGURED]" : "not set") + '\'' + ", defaultTimeoutSeconds=" + defaultTimeoutSeconds
                + ", consistencyCheckTimeoutSeconds=" + consistencyCheckTimeoutSeconds + '}';
    }

    /**
     * Validates that when TLS is enabled, all certificate paths are configured.
     * For production, mTLS with client certificates is required when TLS is enabled.
     */
    public boolean isValidTlsConfiguration() {
        if (!useTls) {
            return true; // No TLS, no cert requirements
        }

        // When TLS is enabled, require all certificate paths for mTLS
        return clientCertPath != null && !clientCertPath.isEmpty() && clientKeyPath != null && !clientKeyPath.isEmpty() && serverCaPath != null && !serverCaPath.isEmpty();
    }
}
