package de.tum.cit.aet.artemis.course.config;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * Presets that write Course Home welcomes, selected by the student's AI preference.
 *
 * @param enabled        whether Home may call a model
 * @param location       inference location of the legacy single preset
 * @param llmPreset      legacy preset for the mode matching its declared location
 * @param localLlmPreset preset for approved local inference
 * @param cloudLlmPreset preset for external inference
 */
@Lazy
@ConfigurationProperties(prefix = CourseHomeAiProperties.PREFIX)
public record CourseHomeAiProperties(boolean enabled, @Nullable String location, @Nullable String llmPreset, @Nullable String localLlmPreset, @Nullable String cloudLlmPreset) {

    static final String PREFIX = "artemis.course-home.ai";

    static final String PRESET_KEY = PREFIX + ".llm-preset";

    static final String LOCAL_PRESET_KEY = PREFIX + ".local-llm-preset";

    static final String CLOUD_PRESET_KEY = PREFIX + ".cloud-llm-preset";

    public CourseHomeAiProperties {
        location = location == null ? null : location.strip().toUpperCase(Locale.ROOT);
        llmPreset = normalize(llmPreset);
        localLlmPreset = normalize(localLlmPreset);
        cloudLlmPreset = normalize(cloudLlmPreset);
    }

    @Nullable
    private static String normalize(@Nullable String preset) {
        return preset == null || preset.isBlank() ? null : preset.strip();
    }

    /**
     * Finds the configured welcome preset for the selected inference mode.
     *
     * @param decision the student's recorded AI choice
     * @return the matching preset, if enabled and configured
     */
    public Optional<String> presetFor(AiSelectionDecision decision) {
        if (!enabled) {
            return Optional.empty();
        }
        return switch (decision) {
            case LOCAL_AI -> Optional.ofNullable(localLlmPreset != null ? localLlmPreset : "LOCAL".equals(location) ? llmPreset : null);
            case CLOUD_AI -> Optional.ofNullable(cloudLlmPreset != null ? cloudLlmPreset : "CLOUD".equals(location) ? llmPreset : null);
            case NO_AI -> Optional.empty();
        };
    }

    public boolean isConfigured() {
        return presetFor(AiSelectionDecision.LOCAL_AI).isPresent() || presetFor(AiSelectionDecision.CLOUD_AI).isPresent();
    }
}
