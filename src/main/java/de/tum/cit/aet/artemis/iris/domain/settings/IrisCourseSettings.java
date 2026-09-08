package de.tum.cit.aet.artemis.iris.domain.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import java.io.Serializable;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON object persisted for Iris course settings.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseSettings(boolean enabled, @Size(max = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) @Nullable String customInstructions, IrisPipelineVariant variant,
        IrisSupportLevel supportLevel, @Valid @Nullable IrisRateLimitConfiguration rateLimit, @Nullable Boolean proactiveStruggleEnabled,
        @Nullable Boolean legacyBuildTriggersEnabled) implements Serializable {

    private static final IrisCourseSettings DEFAULT = new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null, null, null);

    @JsonCreator
    public IrisCourseSettings(@JsonProperty("enabled") boolean enabled, @JsonProperty("customInstructions") @Nullable String customInstructions,
            @JsonProperty("variant") IrisPipelineVariant variant, @JsonProperty("supportLevel") @Nullable IrisSupportLevel supportLevel,
            @JsonProperty("rateLimit") @Valid IrisRateLimitConfiguration rateLimit, @JsonProperty("proactiveStruggleEnabled") @Nullable Boolean proactiveStruggleEnabled,
            @JsonProperty("legacyBuildTriggersEnabled") @Nullable Boolean legacyBuildTriggersEnabled) {
        this.enabled = enabled;
        this.customInstructions = sanitizeCustomInstructions(customInstructions);
        this.variant = Objects.requireNonNullElse(variant, IrisPipelineVariant.DEFAULT);
        this.supportLevel = Objects.requireNonNullElse(supportLevel, IrisSupportLevel.MODERATE);
        this.rateLimit = rateLimit; // null = use defaults, non-null = explicit override (even if values are null = unlimited)
        // Neither flag is defaulted here: null must survive to the update path, which reads it as
        // "the request says nothing about this field" and merges the stored value (see below).
        this.proactiveStruggleEnabled = proactiveStruggleEnabled;
        this.legacyBuildTriggersEnabled = legacyBuildTriggersEnabled;
    }

    /**
     * Whether Iris watches this course's students for signs of struggle and offers help unprompted.
     * <p>
     * Nullable like {@link #legacyBuildTriggersEffective()}, with the opposite default: {@code null} means the
     * payload said nothing, and the update path keeps what is stored rather than reading the omission as an
     * opt-out. Read the decision here, never off the raw component, which unboxes to an NPE on those rows.
     *
     * @return the effective decision, defaulting to off
     */
    @JsonIgnore
    public boolean proactiveStruggleEffective() {
        return Boolean.TRUE.equals(proactiveStruggleEnabled);
    }

    /**
     * Whether Artemis' own build-triggered proactive Iris events (build_failed / progress_stalled,
     * {@code IrisChatSessionService#handleNewResultEvent}) may fire for this course.
     * <p>
     * Three states, and the distinction is load-bearing. {@code null} means nobody ever decided:
     * a settings row written before this field existed has no key, and a full PUT from a client that
     * does not know the field omits it. Both must keep whatever is stored rather than silently
     * flipping a course, so the default lives HERE and nowhere else, and the update path merges a
     * null request value from the persisted one. {@code true} preserves the behaviour every course
     * had before the field existed; {@code false} is an explicit opt-out, which is what a course
     * running this thesis' struggle detection wants so build-triggered proactivity has one owner.
     *
     * @return the effective decision, defaulting to on
     */
    @JsonIgnore
    public boolean legacyBuildTriggersEffective() {
        return Objects.requireNonNullElse(legacyBuildTriggersEnabled, true);
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
        return new IrisCourseSettings(enabled, customInstructions, variant, supportLevel, rateLimit, null, null);
    }

    /**
     * Like {@link #of(boolean, String, IrisPipelineVariant, IrisSupportLevel, IrisRateLimitConfiguration)} but carries the
     * struggle-detection decision. Used by the update path and the paths that must set or preserve it.
     *
     * @param enabled                  desired enabled flag
     * @param customInstructions       optional custom instructions
     * @param variant                  desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel             desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit                optional rate limit overrides
     * @param proactiveStruggleEnabled whether struggle detection is on for the course (null = undecided, see
     *                                     {@link #proactiveStruggleEffective()})
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant, @Nullable IrisSupportLevel supportLevel,
            @Nullable IrisRateLimitConfiguration rateLimit, @Nullable Boolean proactiveStruggleEnabled) {
        return new IrisCourseSettings(enabled, customInstructions, variant, supportLevel, rateLimit, proactiveStruggleEnabled, null);
    }

    /**
     * Like {@link #of(boolean, String, IrisPipelineVariant, IrisSupportLevel, IrisRateLimitConfiguration, Boolean)} but
     * carries the legacy-trigger decision as well. Both nullable values are passed through UNCHANGED, never
     * defaulted: only the update path may resolve a null, by merging the persisted value.
     *
     * @param enabled                    desired enabled flag
     * @param customInstructions         optional custom instructions
     * @param variant                    desired variant (defaults to {@link IrisPipelineVariant#DEFAULT})
     * @param supportLevel               desired instructional support level (defaults to {@link IrisSupportLevel#MODERATE})
     * @param rateLimit                  optional rate limit overrides
     * @param proactiveStruggleEnabled   whether struggle detection is on (null = undecided, see
     *                                       {@link #proactiveStruggleEffective()})
     * @param legacyBuildTriggersEnabled whether Artemis' own build-triggered events may fire (null = undecided, see
     *                                       {@link #legacyBuildTriggersEffective()})
     * @return sanitized instance
     */
    public static IrisCourseSettings of(boolean enabled, @Nullable String customInstructions, @Nullable IrisPipelineVariant variant, @Nullable IrisSupportLevel supportLevel,
            @Nullable IrisRateLimitConfiguration rateLimit, @Nullable Boolean proactiveStruggleEnabled, @Nullable Boolean legacyBuildTriggersEnabled) {
        return new IrisCourseSettings(enabled, customInstructions, variant, supportLevel, rateLimit, proactiveStruggleEnabled, legacyBuildTriggersEnabled);
    }
}
