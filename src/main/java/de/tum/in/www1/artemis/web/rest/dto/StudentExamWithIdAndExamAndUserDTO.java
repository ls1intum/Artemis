package de.tum.in.www1.artemis.web.rest.dto;

public record StudentExamWithIdAndExamAndUserDTO(long id, ExamWithIdAndCourseDTO exam, UserWithIdAndLoginDTO user) {
}
