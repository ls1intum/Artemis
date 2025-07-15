package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.HasEnabledCategories;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * An {@link IrisSubSettings} implementation for chat settings.
 * Chat settings notably provide settings for the rate limit.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisProgrammingExerciseChatSubSettings extends IrisSubSettings implements HasEnabledCategories {

    @Nullable
    private Integer rateLimit;

    @Nullable
    private Integer rateLimitTimeframeHours;

    private SortedSet<String> enabledForCategories = new TreeSet<>();

    private SortedSet<String> disabledProactiveEvents = new TreeSet<>();

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

    @Override
    public SortedSet<String> getEnabledForCategories() {
        return enabledForCategories;
    }

    @Override
    public void setEnabledForCategories(SortedSet<String> enabledForCategories) {
        this.enabledForCategories = enabledForCategories;
    }

    public SortedSet<String> getDisabledProactiveEvents() {
        return disabledProactiveEvents;
    }

    public void setDisabledProactiveEvents(SortedSet<String> disabledProactiveEvents) {
        this.disabledProactiveEvents = disabledProactiveEvents;
    }

    @Nullable
    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(@Nullable String customInstructions) {
        this.customInstructions = customInstructions;
    }
}
