package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineLinkRequestDTO(String campusOnlineCourseId, String responsibleInstructor, String department, String studyProgram) {
}
