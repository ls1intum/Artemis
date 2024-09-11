package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamWithIdAndExamAndUserDTO(long id, ExamWithIdAndCourseDTO exam, UserWithIdAndLoginDTO user) {
}
