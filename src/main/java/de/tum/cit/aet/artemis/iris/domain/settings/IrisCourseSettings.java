package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import java.io.Serializable;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON object persisted for Iris course settings.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseSettings(boolean enabled, boolean promptingModeEnabled, @Size(max = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) @Nullable String customInstructions,
        IrisPipelineVariant variant, IrisSupportLevel supportLevel, @Valid IrisPromptingModeSettings promptingModeSettings, @Valid @Nullable IrisRateLimitConfiguration rateLimit)
        implements Serializable {

    private static final boolean DEFAULT_PROMPTING_MODE_ENABLED = true;

    private static final IrisCourseSettings DEFAULT = new IrisCourseSettings(true, DEFAULT_PROMPTING_MODE_ENABLED, null, IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE,
            IrisPromptingModeSettings.defaultSettings(), null);

    public IrisCourseSettings {
        customInstructions = sanitizeCustomInstructions(customInstructions);
        variant = Objects.requireNonNullElse(variant, IrisPipelineVariant.DEFAULT);
        supportLevel = Objects.requireNonNullElse(supportLevel, IrisSupportLevel.MODERATE);
        promptingModeSettings = Objects.requireNonNullElse(promptingModeSettings, IrisPromptingModeSettings.defaultSettings());
    }

    @JsonCreator
    public IrisCourseSettings(@JsonProperty("enabled") boolean enabled, @JsonProperty("promptingModeEnabled") @Nullable Boolean promptingModeEnabled,
            @JsonProperty("customInstructions") @Nullable String customInstructions, @JsonProperty("variant") IrisPipelineVariant variant,
            @JsonProperty("supportLevel") @Nullable IrisSupportLevel supportLevel, @JsonProperty("promptingModeSettings") @Valid IrisPromptingModeSettings promptingModeSettings,
            @JsonProperty("rateLimit") @Valid IrisRateLimitConfiguration rateLimit) {
        this(enabled, promptingModeEnabled != null ? promptingModeEnabled.booleanValue() : DEFAULT_PROMPTING_MODE_ENABLED, customInstructions, variant, supportLevel,
                promptingModeSettings, rateLimit);
    }

    public static IrisCourseSettings defaultSettings() {
        return DEFAULT;
    }

    private static String sanitizeCustomInstructions(@Nullable String instructions) {
        if (instructions == null) {
            return null;
        }
        var trimmed = instructions.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Creates an object with overrides merged on top of defaults.
     *
     * @param enabled            desired enabled flag
     * @param customInstructions optional custom instructions
     * @param variant            desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel       desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit          optional rate limit overrides
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant, @Nullable IrisSupportLevel supportLevel,
            @Nullable IrisRateLimitConfiguration rateLimit) {
        return of(enabled, DEFAULT_PROMPTING_MODE_ENABLED, IrisPromptingModeSettings.defaultSettings(), customInstructions, variant, supportLevel, rateLimit);
    }

    /**
     * Creates an object with overrides merged on top of defaults.
     *
     * @param enabled              desired enabled flag
     * @param promptingModeEnabled desired prompting mode enabled flag
     * @param customInstructions   optional custom instructions
     * @param variant              desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel         desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit            optional rate limit overrides
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, boolean promptingModeEnabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant,
            @Nullable IrisSupportLevel supportLevel, @Nullable IrisRateLimitConfiguration rateLimit) {
        return of(enabled, promptingModeEnabled, IrisPromptingModeSettings.defaultSettings(), customInstructions, variant, supportLevel, rateLimit);
    }

    /**
     * Creates an object with overrides merged on top of defaults.
     *
     * @param enabled               desired enabled flag
     * @param promptingModeEnabled  desired prompting mode enabled flag
     * @param promptingModeSettings desired prompting-mode quiz settings
     * @param customInstructions    optional custom instructions
     * @param variant               desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel          desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit             optional rate limit overrides
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, boolean promptingModeEnabled, @Nullable IrisPromptingModeSettings promptingModeSettings,
            @Nullable String customInstructions, @Nullable IrisPipelineVariant variant, @Nullable IrisSupportLevel supportLevel, @Nullable IrisRateLimitConfiguration rateLimit) {
        return new IrisCourseSettings(enabled, promptingModeEnabled, customInstructions, variant, supportLevel, promptingModeSettings, rateLimit);
    }
}
