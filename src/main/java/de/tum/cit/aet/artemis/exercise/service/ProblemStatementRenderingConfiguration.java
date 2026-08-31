package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for the problem-statement render endpoint, under {@code artemis.problem-statement-rendering}.
 * <p>
 * The limit lives here rather than as a {@code @Value} on the endpoint so it can be validated on its own: an
 * architecture rule forbids test classes from importing a {@code @RestController}, which would otherwise leave the
 * check unexercised.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "artemis.problem-statement-rendering")
public class ProblemStatementRenderingConfiguration {

    /** Largest number of test results a single render request may carry. */
    private int maxTestResults = 1000;

    /** Public so the check can be exercised directly; a test may not import the endpoint that consumes the value. */
    @PostConstruct
    public void rejectNegativeMaxTestResults() {
        // The endpoint compares with `>`, so a negative limit rejects every request that carries test results at all,
        // including an empty list, with 422. From the outside that is silent: the endpoint keeps answering, just never
        // with a rendering. Failing here turns it into an error naming the property and the offending value instead.
        if (maxTestResults < 0) {
            throw new IllegalArgumentException("artemis.problem-statement-rendering.max-test-results must not be negative, but was " + maxTestResults);
        }
    }

    public int getMaxTestResults() {
        return maxTestResults;
    }

    public void setMaxTestResults(int maxTestResults) {
        this.maxTestResults = maxTestResults;
    }
}
