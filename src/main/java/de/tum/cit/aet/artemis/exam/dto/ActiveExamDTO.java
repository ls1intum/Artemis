package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExamMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveExamDTO(long id, @NotBlank String title, @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate, ExamMode examMode,
        @NotNull CourseForActiveExamDTO course) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseForActiveExamDTO(long id, @NotBlank String title) {
    }

    public ActiveExamDTO(long id, String title, ZonedDateTime startDate, ZonedDateTime endDate, ExamMode examMode, long courseId, String courseTitle) {
        this(id, title, startDate, endDate, examMode, new CourseForActiveExamDTO(courseId, courseTitle));
    }
}
