package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for the settings for the chat in a text exercise.
 */
@Entity
@DiscriminatorValue("LECTURE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureChatSubSettings extends IrisSubSettings {

    /**
     * Maximum number of messages allowed within the specified timeframe.
     * Must be a positive integer or null to disable rate limiting.
     */
    @Nullable
    @Min(1)
    @Column(name = "rate_limit")
    private Integer rateLimit;

    /**
     * Timeframe in hours for the rate limit.
     * Must be a positive integer when rate limit is set.
     */
    @Nullable
    @Min(1)
    @Column(name = "rate_limit_timeframe_hours")
    private Integer rateLimitTimeframeHours;

    @Nullable
    @Column(name = "custom_instructions", length = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH)
    private String customInstructions;

    @Nullable
    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(@Nullable Integer rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Nullable
    public Integer getRateLimitTimeframeHours() {
        return rateLimitTimeframeHours;
    }

    public void setRateLimitTimeframeHours(@Nullable Integer rateLimitTimeframeHours) {
        this.rateLimitTimeframeHours = rateLimitTimeframeHours;
    }

    @Nullable
    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(@Nullable String customInstructions) {
        this.customInstructions = customInstructions;
    }
}
