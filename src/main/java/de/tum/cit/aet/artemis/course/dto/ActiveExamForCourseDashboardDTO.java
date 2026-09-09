package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/** Active exam shown above the course dashboard. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveExamForCourseDashboardDTO(long id, String title, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam, ActiveExamCourseReferenceDTO course) {

    /** Maps an active exam without exposing its exercise groups or registered users. */
    public static ActiveExamForCourseDashboardDTO of(Exam exam) {
        return new ActiveExamForCourseDashboardDTO(exam.getId(), exam.getTitle(), exam.getStartDate(), exam.getEndDate(), exam.isTestExam(),
                new ActiveExamCourseReferenceDTO(exam.getCourse().getId(), exam.getCourse().getTitle()));
    }

    /** Course identity used by the active-exam navigation link. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ActiveExamCourseReferenceDTO(long id, String title) {
    }
}
