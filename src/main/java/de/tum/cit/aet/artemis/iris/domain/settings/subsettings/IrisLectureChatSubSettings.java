package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * An {@link IrisSubSettings} implementation for the settings for the chat in a text exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisLectureChatSubSettings extends IrisSubSettings {

    /**
     * Maximum number of messages allowed within the specified timeframe.
     * Must be a positive integer or null to disable rate limiting.
     */
    @Nullable
    @Min(1)
    private Integer rateLimit;

    /**
     * Timeframe in hours for the rate limit.
     * Must be a positive integer when rate limit is set.
     */
    @Nullable
    @Min(1)
    private Integer rateLimitTimeframeHours;

    @Nullable
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
