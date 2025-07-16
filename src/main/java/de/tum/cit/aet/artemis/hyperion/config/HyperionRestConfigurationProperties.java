package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Hyperion REST client.
 */
@Component
@ConfigurationProperties(prefix = "artemis.hyperion")
@Profile(PROFILE_HYPERION)
public class HyperionRestConfigurationProperties {

    /**
     * Base URL for the Hyperion service
     */
    private String url = "http://localhost:8000";

    /**
     * API key for authentication
     */
    private String apiKey;

    /**
     * Connection timeout for HTTP requests
     */
    private Duration connectionTimeout = Duration.ofSeconds(30);

    /**
     * Read timeout for HTTP requests
     */
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * Timeout configuration for different operations
     */
    private final Timeouts timeouts = new Timeouts();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    /**
     * Timeout configuration for specific operations
     */
    public static class Timeouts {

        /**
         * Timeout for consistency check operations
         */
        private Duration consistencyCheck = Duration.ofMinutes(2);

        /**
         * Timeout for problem statement rewriting operations
         */
        private Duration rewriteProblemStatement = Duration.ofMinutes(1);

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
    }
}
