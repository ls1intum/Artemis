package de.tum.cit.aet.artemis.iris.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The course settings accepted and returned by the Iris settings resource.
 *
 * @param enabled                    whether Iris is enabled for this course
 * @param customInstructions         optional instructor instructions for Iris
 * @param variant                    the pipeline variant
 * @param supportLevel               the instructional support level, optional in requests
 * @param rateLimit                  optional course-level override
 * @param proactiveStruggleEnabled   the struggle detection flag, absent to preserve the stored value
 * @param legacyBuildTriggersEnabled the build trigger flag, absent to preserve the stored value
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisCourseSettingsDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
        @Size(max = IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) @Nullable String customInstructions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable IrisPipelineVariant variant, @Nullable IrisSupportLevel supportLevel,
        @Valid @Nullable IrisRateLimitConfigurationDTO rateLimit, @Nullable Boolean proactiveStruggleEnabled, @Schema(types = {
                "boolean", "null" }) @Nullable Boolean legacyBuildTriggersEnabled){

    public IrisCourseSettingsDTO {
        // The domain request was trimmed before Bean Validation checked its length.
        customInstructions = IrisCourseSettings.sanitizeCustomInstructions(customInstructions);
    }

    /**
     * @param settings the stored course settings
     * @return the equivalent REST payload
     */
    public static IrisCourseSettingsDTO of(IrisCourseSettings settings) {
        var rateLimit = settings.rateLimit();
        return new IrisCourseSettingsDTO(settings.enabled(), settings.customInstructions(), settings.variant(), settings.supportLevel(),
                rateLimit == null ? null : IrisRateLimitConfigurationDTO.of(rateLimit), settings.proactiveStruggleEnabled(), settings.legacyBuildTriggersEnabled());
    }

    /**
     * @return course settings with domain defaults and both optional flags preserved
     */
    public IrisCourseSettings toDomain() {
        return IrisCourseSettings.of(enabled, customInstructions, variant, supportLevel, rateLimit == null ? null : rateLimit.toDomain(), proactiveStruggleEnabled,
                legacyBuildTriggersEnabled);
    }
}
