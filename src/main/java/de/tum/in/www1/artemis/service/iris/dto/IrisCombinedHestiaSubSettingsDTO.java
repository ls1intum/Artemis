package de.tum.in.www1.artemis.service.iris.dto;

import java.util.Set;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedHestiaSubSettingsDTO(boolean enabled, @Nullable Set<String> allowedModels, @Nullable String preferredModel, @Nullable IrisTemplate template) {
}
