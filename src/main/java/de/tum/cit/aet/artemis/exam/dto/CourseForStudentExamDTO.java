package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Minimal projection of a {@link Course}, nested inside {@link ExamForStudentExamDTO}.
 * Carries exactly the fields the client's {@code accountService.setAccessRightsForCourse} needs to compute
 * the current user's course-level authorization flags (isAtLeastTutor/Editor/Instructor), plus the id needed
 * to link back to the course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForStudentExamDTO(long id, String instructorGroupName, String editorGroupName, String teachingAssistantGroupName) {

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
        return new CourseForStudentExamDTO(course.getId(), course.getInstructorGroupName(), course.getEditorGroupName(), course.getTeachingAssistantGroupName());
    }
}
