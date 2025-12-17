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
public record IrisCourseSettings(boolean enabled, @Size(max = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) @Nullable String customInstructions, IrisPipelineVariant variant,
        @Valid @Nullable IrisRateLimitConfiguration rateLimit) implements Serializable {

    private static final IrisCourseSettings DEFAULT = new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, null);

    @JsonCreator
    public IrisCourseSettings(@JsonProperty("enabled") boolean enabled, @JsonProperty("customInstructions") @Nullable String customInstructions,
            @JsonProperty("variant") IrisPipelineVariant variant, @JsonProperty("rateLimit") @Valid IrisRateLimitConfiguration rateLimit) {
        this.enabled = enabled;
        this.customInstructions = sanitizeCustomInstructions(customInstructions);
        this.variant = Objects.requireNonNullElse(variant, IrisPipelineVariant.DEFAULT);
        this.rateLimit = rateLimit; // null = use defaults, non-null = explicit override (even if values are null = unlimited)
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
     * Creates a DTO with overrides merged on top of defaults.
     *
     * @param enabled            desired enabled flag
     * @param customInstructions optional custom instructions
     * @param variant            desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param rateLimit          optional rate limit overrides
     * @return sanitized DTO instance
     */
    public static IrisCourseSettings of(boolean enabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant,
            @Nullable IrisRateLimitConfiguration rateLimit) {
        return new IrisCourseSettings(enabled, customInstructions, variant, rateLimit);
    }
}
