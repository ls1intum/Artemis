package de.tum.cit.aet.artemis.assessment.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Configuration for feedback-related settings.
 * This class provides both instance-based and static access to the max feedback length configuration.
 * The static access is needed for use in entity classes like {@link de.tum.cit.aet.artemis.assessment.domain.Feedback}
 * which cannot have Spring dependencies injected.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy(false)
public class FeedbackConfiguration {

    private static int maxFeedbackLengthStatic = 20_000;

    /**
     * Upper bound for persisted long feedback text of a single test case / assertion.
     * <p>
     * In practice, feedback is short (typically &lt; 1k chars). Very large feedback texts
     * are almost always caused by runaway output (e.g. infinite loops, excessive logging,
     * repeated stack traces) and do not provide additional value to students.
     * <p>
     * Build logs are stored separately; this limit protects the database from
     * accidental storage explosions while keeping enough context for debugging.
     */
    @Value("${artemis.feedback.max-feedback-length:20000}")
    private int maxFeedbackLength;

    @PostConstruct
    void init() {
        maxFeedbackLengthStatic = maxFeedbackLength;
    }

    /**
     * Gets the maximum length for long feedback text.
     * This is the instance method for use in Spring-managed beans.
     *
     * @return the configured maximum length for long feedback
     */
    public int getMaxFeedbackLength() {
        return maxFeedbackLength;
    }

    /**
     * Gets the maximum length for long feedback text.
     * This static method is for use in non-Spring-managed classes like entities.
     * Falls back to the default value of 20,000 if Spring context is not initialized.
     *
     * @return the configured maximum length for long feedback
     */
    public static int getMaxFeedbackLengthStatic() {
        return maxFeedbackLengthStatic;
    }
}
