package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineLinkRequestDTO(@NotBlank String campusOnlineCourseId, String responsibleInstructor, String department, String studyProgram) {
}
