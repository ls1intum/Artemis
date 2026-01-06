package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForQuizExerciseDTO(Long id, String title, String shortName, String studentGroupName, String teachingAssistantGroupName, String editorGroupName,
        String instructorGroupName, ZonedDateTime startDate, ZonedDateTime endDate, Integer accuracyOfScores) {

    /**
     * Create a CourseForQuizExerciseDTO from a Course
     *
     * @param course the course to convert
     * @return the converted CourseForQuizExerciseDTO
     */
    public static CourseForQuizExerciseDTO of(final Course course) {
        if (course == null) {
            return null;
        }

        return new CourseForQuizExerciseDTO(course.getId(), course.getTitle(), course.getShortName(), course.getStudentGroupName(), course.getTeachingAssistantGroupName(),
                course.getEditorGroupName(), course.getInstructorGroupName(), course.getStartDate(), course.getEndDate(), course.getAccuracyOfScores());
    }

}
