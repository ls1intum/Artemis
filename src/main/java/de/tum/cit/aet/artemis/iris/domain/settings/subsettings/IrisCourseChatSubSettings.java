package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * An {@link IrisSubSettings} implementation for course chat settings.
 * Chat settings notably provide settings for the rate limit.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisCourseChatSubSettings extends IrisSubSettings {

    @Nullable
    private Integer rateLimit;

    @Nullable
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
