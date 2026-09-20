package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Slim course projection embedded in the exam response DTOs ({@link ExamDTO} and {@link ExamWithExerciseGroupsDTO}).
 * <p>
 * Carries exactly the {@code exam.course} fields the exam management screens read: the id (routing / API links) and
 * title (exam detail), the {@code testCourse} flag (exam deletion summary), and the three group names. The group names
 * are load-bearing: the client computes {@code isAtLeast{Tutor,Editor,Instructor}} from them in
 * {@code AccountService.setAccessRightsForCourse} (invoked by the exam service's response converter), which gates the
 * edit / add-exercise / reset actions across all exam screens. Dropping them would silently disable those controls for
 * non-admin editors and instructors. All other columns of the previously serialized full {@link Course} entity are
 * intentionally omitted for data economy (no exam-response consumer reads them).
 *
 * @param id         the id of the course
 * @param title      the title of the course
 * @param testCourse whether the course is a test course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForExamDTO(long id, @Nullable String title, boolean testCourse) {

    /**
     * Builds the slim course projection from a course entity.
     *
     * @param course the course (eagerly loaded on the exam); may be {@code null}
     * @return the slim course DTO, or {@code null} if the course is {@code null}
     */
    @Nullable
    public static CourseForExamDTO of(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new CourseForExamDTO(course.getId(), course.getTitle(), course.isTestCourse());
    }
}
