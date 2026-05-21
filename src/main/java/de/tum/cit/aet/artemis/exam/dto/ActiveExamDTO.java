package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveExamDTO(long id, @NotBlank String title, @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate, boolean testExam,
        @NotNull CourseForActiveExamDTO course) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseForActiveExamDTO(long id, @NotBlank String title) {
    }

    public ActiveExamDTO(long id, String title, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam, long courseId, String courseTitle) {
        this(id, title, startDate, endDate, testExam, new CourseForActiveExamDTO(courseId, courseTitle));
    }
}
