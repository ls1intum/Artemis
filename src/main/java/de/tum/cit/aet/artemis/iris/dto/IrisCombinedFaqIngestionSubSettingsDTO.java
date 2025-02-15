package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCombinedFaqIngestionSubSettingsDTO(boolean enabled) {
}
