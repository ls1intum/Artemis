package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.HasEnabledCategories;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * An {@link IrisSubSettings} implementation for the settings for the chat in a text exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisTextExerciseChatSubSettings extends IrisSubSettings implements HasEnabledCategories {

    @Nullable
    private Integer rateLimit;

    @Nullable
    private Integer rateLimitTimeframeHours;

    @Nullable
    private SortedSet<String> enabledForCategories = new TreeSet<>();

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
    public SortedSet<String> getEnabledForCategories() {
        return enabledForCategories;
    }

    public void setEnabledForCategories(@Nullable SortedSet<String> enabledForCategories) {
        this.enabledForCategories = enabledForCategories;
    }

    @Nullable
    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(@Nullable String customInstructions) {
        this.customInstructions = customInstructions;
    }
}
