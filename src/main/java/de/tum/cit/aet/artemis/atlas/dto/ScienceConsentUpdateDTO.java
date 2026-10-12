package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceConsentUpdateDTO(@NotNull Boolean active) {
}
