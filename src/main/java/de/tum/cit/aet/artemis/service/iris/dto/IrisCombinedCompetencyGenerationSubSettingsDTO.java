package de.tum.cit.aet.artemis.service.iris.dto;

import java.util.Set;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.iris.IrisTemplate;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedCompetencyGenerationSubSettingsDTO(boolean enabled, @Nullable Set<String> allowedModels, @Nullable String preferredModel,
        @Nullable IrisTemplate template) {
}
