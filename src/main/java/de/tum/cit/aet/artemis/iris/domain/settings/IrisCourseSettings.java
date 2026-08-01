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
public record IrisCourseSettings(boolean enabled, boolean askUserModeEnabled, @Size(max = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) @Nullable String customInstructions,
        IrisPipelineVariant variant, IrisSupportLevel supportLevel, @Valid IrisAskUserModeSettings askUserModeSettings, @Valid @Nullable IrisRateLimitConfiguration rateLimit)
        implements Serializable {

    private static final boolean DEFAULT_ASK_USER_MODE_ENABLED = true;

    private static final IrisCourseSettings DEFAULT = new IrisCourseSettings(true, DEFAULT_ASK_USER_MODE_ENABLED, null, IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE,
            IrisAskUserModeSettings.defaultSettings(), null);

    public IrisCourseSettings {
        customInstructions = sanitizeCustomInstructions(customInstructions);
        variant = Objects.requireNonNullElse(variant, IrisPipelineVariant.DEFAULT);
        supportLevel = Objects.requireNonNullElse(supportLevel, IrisSupportLevel.MODERATE);
        askUserModeSettings = Objects.requireNonNullElse(askUserModeSettings, IrisAskUserModeSettings.defaultSettings());
    }

    @JsonCreator
    public IrisCourseSettings(@JsonProperty("enabled") boolean enabled, @JsonProperty("askUserModeEnabled") @Nullable Boolean askUserModeEnabled,
            @JsonProperty("customInstructions") @Nullable String customInstructions, @JsonProperty("variant") IrisPipelineVariant variant,
            @JsonProperty("supportLevel") @Nullable IrisSupportLevel supportLevel, @JsonProperty("askUserModeSettings") @Valid IrisAskUserModeSettings askUserModeSettings,
            @JsonProperty("rateLimit") @Valid IrisRateLimitConfiguration rateLimit) {
        this(enabled, askUserModeEnabled != null ? askUserModeEnabled.booleanValue() : DEFAULT_ASK_USER_MODE_ENABLED, customInstructions, variant, supportLevel,
                askUserModeSettings, rateLimit);
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
        return of(enabled, DEFAULT_ASK_USER_MODE_ENABLED, IrisAskUserModeSettings.defaultSettings(), customInstructions, variant, supportLevel, rateLimit);
    }

    /**
     * Creates an object with overrides merged on top of defaults.
     *
     * @param enabled            desired enabled flag
     * @param askUserModeEnabled desired ask-user mode enabled flag
     * @param customInstructions optional custom instructions
     * @param variant            desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel       desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit          optional rate limit overrides
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, boolean askUserModeEnabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant,
            @Nullable IrisSupportLevel supportLevel, @Nullable IrisRateLimitConfiguration rateLimit) {
        return of(enabled, askUserModeEnabled, IrisAskUserModeSettings.defaultSettings(), customInstructions, variant, supportLevel, rateLimit);
    }

    /**
     * Creates an object with overrides merged on top of defaults.
     *
     * @param enabled             desired enabled flag
     * @param askUserModeEnabled  desired ask-user mode enabled flag
     * @param askUserModeSettings desired ask-user-mode quiz settings
     * @param customInstructions  optional custom instructions
     * @param variant             desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel        desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit           optional rate limit overrides
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, boolean askUserModeEnabled, @Nullable IrisAskUserModeSettings askUserModeSettings, @Nullable String customInstructions,
            @Nullable IrisPipelineVariant variant, @Nullable IrisSupportLevel supportLevel, @Nullable IrisRateLimitConfiguration rateLimit) {
        return new IrisCourseSettings(enabled, askUserModeEnabled, customInstructions, variant, supportLevel, askUserModeSettings, rateLimit);
    }
}
