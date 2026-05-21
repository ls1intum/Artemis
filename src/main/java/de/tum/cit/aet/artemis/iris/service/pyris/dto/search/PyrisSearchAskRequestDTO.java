package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisSearchAskRequestDTO(@NotBlank String query, @Min(1) @Max(5) int limit) {
}
