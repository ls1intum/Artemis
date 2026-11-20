package de.tum.cit.aet.artemis.course.config;

import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;

/**
 * Home's welcome provider: the LLM preset it calls and where that preset's inference takes place. Missing values leave Home deterministic.
 *
 * @param enabled   whether Home may call a model
 * @param location  {@code LOCAL} when the preset's endpoint runs within the installation's approved local environment, {@code CLOUD} otherwise; it decides
 *                      which students' consent permits a welcome
 * @param llmPreset the preset under {@code artemis.llm.presets} that writes the welcomes
 */
@Lazy
@ConfigurationProperties(prefix = "artemis.course-home.ai")
public record CourseHomeAiProperties(boolean enabled, @Nullable String location, @Nullable String llmPreset) {

    /** The key that assigns Home's preset. */
    public static final String PRESET_KEY = "artemis.course-home.ai.llm-preset";

    public String providerLocation() {
        return location == null ? "" : location.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Checks whether Home is enabled with a known location and an assigned preset; the preset itself is checked at startup with the LLM catalog.
     *
     * @return true when Home can call its provider
     */
    public boolean isConfigured() {
        return enabled && (providerLocation().equals("LOCAL") || providerLocation().equals("CLOUD")) && llmPreset != null && !llmPreset.isBlank();
    }

    @Override
    public String toString() {
        return "CourseHomeAiProperties[enabled=" + enabled + ", location=" + providerLocation() + ", configured=" + isConfigured() + "]";
    }
}
