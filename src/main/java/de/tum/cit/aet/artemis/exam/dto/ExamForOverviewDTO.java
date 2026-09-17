package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An exam as the course overview sidebar needs it: enough to title, date, sort, group and link to it.
 * <p>
 * The sidebar previously received whole {@code Exam} entities. Everything an exam actually contains — exercise groups,
 * exercises, registered users, grading — belongs to the exam itself and is loaded when a student opens it.
 *
 * @param id            the id of the exam
 * @param title         the title shown on the sidebar card
 * @param moduleNumber  shown as the card subtitle
 * @param visibleDate   when the exam becomes visible; the sidebar hides exams whose visible date has not passed
 * @param startDate     when the exam starts; drives sorting and the "upcoming exam" redirect
 * @param endDate       when the exam ends
 * @param workingTime   the working time in seconds, shown on the card
 * @param examMaxPoints the attainable points, shown on the card
 * @param testExam      whether this is a test exam, which the sidebar groups separately and allows attempts for
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForOverviewDTO(long id, String title, String moduleNumber, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, int workingTime,
        Integer examMaxPoints, boolean testExam) {
}
