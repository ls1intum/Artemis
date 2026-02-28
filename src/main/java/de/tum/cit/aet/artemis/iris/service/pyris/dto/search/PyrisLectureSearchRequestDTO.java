package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureSearchRequestDTO(@NotBlank String query, @Min(1) @Max(20) int limit) {
}
