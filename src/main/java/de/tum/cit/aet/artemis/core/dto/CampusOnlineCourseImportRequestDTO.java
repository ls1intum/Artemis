package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for requesting a full course import from CAMPUSOnline into Artemis.
 * Creates a new Artemis course linked to the specified CAMPUSOnline course.
 *
 * @param campusOnlineCourseId the CAMPUSOnline course ID to import
 * @param shortName            the desired short name for the new Artemis course (alphanumeric, starts with letter)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineCourseImportRequestDTO(@NotBlank String campusOnlineCourseId, @NotBlank @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$") String shortName) {
}
