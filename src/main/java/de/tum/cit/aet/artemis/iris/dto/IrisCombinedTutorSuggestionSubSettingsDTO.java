package de.tum.cit.aet.artemis.iris.dto;

import java.util.Set;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data transfer object for the IrisCombinedTutorSuggestionSubSettings.
 *
 * @param enabled         true if settings are enabled
 * @param allowedVariants a set of allowed variants
 * @param selectedVariant the selected variant
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedTutorSuggestionSubSettingsDTO(boolean enabled, @Nullable Set<String> allowedVariants, @Nullable String selectedVariant) {
}
