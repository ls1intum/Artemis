package de.tum.cit.aet.artemis.iris.dto;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedCompetencyGenerationSubSettingsDTO(boolean enabled, @Nullable Set<String> allowedVariants, @Nullable String selectedVariant) {
}
