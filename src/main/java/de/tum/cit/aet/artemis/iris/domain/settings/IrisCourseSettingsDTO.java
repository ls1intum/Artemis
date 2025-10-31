package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import java.io.Serializable;
import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON DTO persisted for Iris course settings.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseSettingsDTO(boolean enabled, @Size(max = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) @Nullable String customInstructions, IrisPipelineVariant variant,
        @Valid IrisRateLimitConfiguration rateLimit) implements Serializable {

    private static final IrisCourseSettingsDTO DEFAULT = new IrisCourseSettingsDTO(true, null, IrisPipelineVariant.DEFAULT, IrisRateLimitConfiguration.empty());

    @JsonCreator
    public IrisCourseSettingsDTO(@JsonProperty("enabled") boolean enabled, @JsonProperty("customInstructions") @Nullable String customInstructions,
            @JsonProperty("variant") IrisPipelineVariant variant, @JsonProperty("rateLimit") @Valid IrisRateLimitConfiguration rateLimit) {
        this.enabled = enabled;
        this.customInstructions = sanitizeCustomInstructions(customInstructions);
        this.variant = Objects.requireNonNullElse(variant, IrisPipelineVariant.DEFAULT);
        this.rateLimit = Objects.requireNonNullElse(rateLimit, IrisRateLimitConfiguration.empty());
    }

    public static IrisCourseSettingsDTO defaultSettings() {
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
     * Creates a DTO with overrides merged on top of defaults.
     *
     * @param enabled            desired enabled flag
     * @param customInstructions optional custom instructions
     * @param variant            desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param rateLimit          optional rate limit overrides
     * @return sanitized DTO instance
     */
    public static IrisCourseSettingsDTO of(boolean enabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant,
            @Nullable IrisRateLimitConfiguration rateLimit) {
        return new IrisCourseSettingsDTO(enabled, customInstructions, variant, rateLimit);
    }
}
