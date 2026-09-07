package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Slim projection of an {@link Exam} for the admin "upcoming exams" overview table.
 * <p>
 * Carries only what that table renders: the exam link (id / title), the test-exam badge, the linked course (id /
 * title) and the three schedule dates. All other exam fields (review window, results-publication date, course group
 * names, etc.) are intentionally dropped as the page reads none of them.
 *
 * @param id          the id of the exam
 * @param title       the title of the exam
 * @param testExam    whether the exam is a test exam
 * @param course      the (slim) course the exam belongs to
 * @param visibleDate the date from which the exam is visible to students
 * @param startDate   the date from which students can start the exam
 * @param endDate     the date until which students can work on the exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpcomingExamDTO(Long id, String title, Boolean testExam, @Nullable CourseForUpcomingExamDTO course, ZonedDateTime visibleDate, ZonedDateTime startDate,
        ZonedDateTime endDate) {

    /**
     * Slim course projection embedded in an {@link UpcomingExamDTO}. Carries only the id and title the overview links.
     *
     * @param id    the id of the course
     * @param title the title of the course
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseForUpcomingExamDTO(Long id, String title) {

        /**
         * Builds the slim course projection from a course entity.
         *
         * @param course the course
         * @return the slim course DTO
         */
        public static CourseForUpcomingExamDTO of(Course course) {
            return new CourseForUpcomingExamDTO(course.getId(), course.getTitle());
        }
    }

    /**
     * Builds the slim upcoming-exam projection from an exam entity (with its eager course loaded).
     *
     * @param exam the exam
     * @return the slim upcoming-exam DTO
     */
    public static UpcomingExamDTO of(Exam exam) {
        CourseForUpcomingExamDTO courseDTO = exam.getCourse() == null ? null : CourseForUpcomingExamDTO.of(exam.getCourse());
        return new UpcomingExamDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), courseDTO, exam.getVisibleDate(), exam.getStartDate(), exam.getEndDate());
    }
}
