package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for linking an existing Artemis course to a CAMPUSOnline course.
 * Unlike the import request, this does not create a new course but associates
 * an existing one with a CAMPUSOnline course ID and optional metadata.
 *
 * @param campusOnlineCourseId  the CAMPUSOnline course ID to link
 * @param responsibleInstructor the responsible instructor name (optional)
 * @param department            the department name (optional)
 * @param studyProgram          the study program name (optional)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineLinkRequestDTO(@NotBlank String campusOnlineCourseId, String responsibleInstructor, String department, String studyProgram) {
}
