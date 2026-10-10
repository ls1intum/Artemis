package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the runtime slow-query detector.
 * Bound from the {@code artemis.performance} namespace in {@code application-e2e-performance.yml}.
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@ConfigurationProperties(prefix = "artemis.performance")
public class SlowQueryProperties {

    /**
     * Queries whose execution time exceeds this value (in milliseconds) are captured.
     * Default: 100 ms.
     */
    private long slowQueryThresholdMs = 100;

    /**
     * If the same normalised SQL template is executed more than this many times within a
     * single HTTP request, the pattern is flagged as an N+1 suspect.
     * Default: 5.
     */
    private int n1DetectionThreshold = 5;

    /**
     * Circuit-breaker: stop recording after this many entries to prevent OOM on runaway queries.
     * Default: 10 000.
     */
    private int maxRecordedQueries = 10_000;

    // --- getters & setters ---

    public long getSlowQueryThresholdMs() {
        return slowQueryThresholdMs;
    }

    public void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    public int getN1DetectionThreshold() {
        return n1DetectionThreshold;
    }

    public void setN1DetectionThreshold(int n1DetectionThreshold) {
        this.n1DetectionThreshold = n1DetectionThreshold;
    }

    public int getMaxRecordedQueries() {
        return maxRecordedQueries;
    }

    public void setMaxRecordedQueries(int maxRecordedQueries) {
        this.maxRecordedQueries = maxRecordedQueries;
    }
}
