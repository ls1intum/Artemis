package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineCourseImportRequestDTO(@NotBlank String campusOnlineCourseId, @NotBlank @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$") String shortName) {
}
