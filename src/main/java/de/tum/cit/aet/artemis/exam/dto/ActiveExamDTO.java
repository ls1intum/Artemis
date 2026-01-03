package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveExamDTO(Long id, String title, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam, CourseForActiveExamDTO course) {

    public record CourseForActiveExamDTO(Long id, String title) {
    }

    public ActiveExamDTO(Long id, String title, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam, Long courseId, String courseTitle) {
        this(id, title, startDate, endDate, testExam, new CourseForActiveExamDTO(courseId, courseTitle));
    }
}
