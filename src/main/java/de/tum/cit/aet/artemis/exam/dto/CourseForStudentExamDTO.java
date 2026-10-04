package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Minimal projection of a {@link Course}, nested inside {@link ExamForStudentExamDTO} and, via {@link
 * de.tum.cit.aet.artemis.exam.dto.conduction.ExamForConductionDTO}, inside the conduction and summary payloads.
 * Carries the course id, which is all the client's {@code accountService.setAccessRightsForCourse} needs to
 * resolve the current user's course-level authorization flags (isAtLeastTutor/Editor/Instructor) from their
 * course roles, and to link back to the course, plus {@code accuracyOfScores}, which the exam-taking client's
 * score-rounding utilities ({@code roundScorePercentSpecifiedByCourseSettings} et al.) read from {@code exam.course}.
 * <p>
 * The complaint limits are read by the exam summary's complaint flow during the student review period:
 * {@code complaints-form.component} caps a new complaint at {@code course.maxComplaintTextLimit} (a missing value
 * falls back to 2000 and silently blocks longer complaints on courses configured above that), and
 * {@code complaint-request} / {@code complaint-response} render an existing complaint with both limits as required
 * inputs. {@code athenaFormativeFeedbackEnabled} is read by {@code exam-request-ai-feedback-button.component} to
 * gate the test-exam AI feedback button on the course-level Athena formative feedback setting.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForStudentExamDTO(long id, Integer accuracyOfScores, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean athenaFormativeFeedbackEnabled) {

    /**
     * Converts a Course into a CourseForStudentExamDTO.
     *
     * @param course the course to convert
     * @return the converted DTO, or null if the course is null
     */
    public static CourseForStudentExamDTO of(Course course) {
        if (course == null) {
            return null;
        }
        return new CourseForStudentExamDTO(course.getId(), course.getAccuracyOfScores(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(),
                course.isAthenaFormativeFeedbackEnabled());
    }
}
