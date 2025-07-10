package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

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
 *     timeouts:
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
     * Timeout configurations for various Hyperion operations.
     */
    private final Timeouts timeouts = new Timeouts();

    public Timeouts getTimeouts() {
        return timeouts;
    }

    /**
     * Timeout configurations for Hyperion service operations.
     * These are business logic timeouts, not gRPC connection timeouts.
     */
    public static class Timeouts {

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
            return "Timeouts{" + "consistencyCheck=" + consistencyCheck + ", rewriteProblemStatement=" + rewriteProblemStatement + '}';
        }
    }

    @Override
    public String toString() {
        return "HyperionConfigurationProperties{" + "timeouts=" + timeouts + '}';
    }
}
