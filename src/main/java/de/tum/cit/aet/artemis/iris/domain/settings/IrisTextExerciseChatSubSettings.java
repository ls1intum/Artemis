package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for the settings for the chat in a text exercise.
 */
@Entity
@DiscriminatorValue("TEXT_EXERCISE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTextExerciseChatSubSettings extends IrisSubSettings implements HasEnabledCategories {

    @Nullable
    @Column(name = "rate_limit")
    private Integer rateLimit;

    @Nullable
    @Column(name = "rate_limit_timeframe_hours")
    private Integer rateLimitTimeframeHours;

    @Nullable
    @Column(name = "enabled_for_categories")
    @Convert(converter = IrisListConverter.class)
    private SortedSet<String> enabledForCategories = new TreeSet<>();

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
