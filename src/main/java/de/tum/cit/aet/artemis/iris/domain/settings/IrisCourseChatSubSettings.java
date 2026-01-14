package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for course chat settings.
 * Chat settings notably provide settings for the rate limit.
 */
@Entity
@DiscriminatorValue("COURSE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCourseChatSubSettings extends IrisSubSettings {

    @Nullable
    @Column(name = "rate_limit")
    private Integer rateLimit;

    @Nullable
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
