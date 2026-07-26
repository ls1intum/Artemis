package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Minimal projection of a {@link Course}, nested inside {@link ExamForStudentExamDTO} and, via {@link
 * de.tum.cit.aet.artemis.exam.dto.conduction.ExamForConductionDTO}, inside the conduction and summary payloads.
 * Carries the fields the client's {@code accountService.setAccessRightsForCourse} needs to compute
 * the current user's course-level authorization flags (isAtLeastTutor/Editor/Instructor), the id needed
 * to link back to the course, and {@code accuracyOfScores}, which the exam-taking client's score-rounding
 * utilities ({@code roundScorePercentSpecifiedByCourseSettings} et al.) read from {@code exam.course}.
 * <p>
 * The complaint limits are read by the exam summary's complaint flow during the student review period:
 * {@code complaints-form.component} caps a new complaint at {@code course.maxComplaintTextLimit} (a missing value
 * falls back to 2000 and silently blocks longer complaints on courses configured above that), and
 * {@code complaint-request} / {@code complaint-response} render an existing complaint with both limits as required
 * inputs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForStudentExamDTO(long id, String instructorGroupName, String editorGroupName, String teachingAssistantGroupName, Integer accuracyOfScores,
        int maxComplaintTextLimit, int maxComplaintResponseTextLimit) {

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
        return new CourseForStudentExamDTO(course.getId(), course.getInstructorGroupName(), course.getEditorGroupName(), course.getTeachingAssistantGroupName(),
                course.getAccuracyOfScores(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit());
    }
}
