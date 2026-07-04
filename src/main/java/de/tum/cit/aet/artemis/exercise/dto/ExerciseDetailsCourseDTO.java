package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

/**
 * DTO containing the narrow course context required by the student exercise details view.
 *
 * @param id                                    the course identifier
 * @param title                                 the course title, if available
 * @param shortName                             the course short name, if available
 * @param studentGroupName                      the student group used for client-side access-right reconstruction, if available
 * @param teachingAssistantGroupName            the teaching assistant group used for client-side access-right reconstruction, if available
 * @param editorGroupName                       the editor group used for client-side access-right reconstruction, if available
 * @param instructorGroupName                   the instructor group used for client-side access-right reconstruction, if available
 * @param accuracyOfScores                      the configured score precision, if available
 * @param complaintsEnabled                     whether complaints are enabled, if configured
 * @param requestMoreFeedbackEnabled            whether feedback requests are enabled, if configured
 * @param courseInformationSharingConfiguration the communication and messaging configuration, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsCourseDTO(Long id, @Nullable String title, @Nullable String shortName, @Nullable String studentGroupName, @Nullable String teachingAssistantGroupName,
        @Nullable String editorGroupName, @Nullable String instructorGroupName, @Nullable Integer accuracyOfScores, @Nullable Boolean complaintsEnabled,
        @Nullable Boolean requestMoreFeedbackEnabled, @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration) {

    /**
     * Maps a course without exposing its exercises, users, or other entity associations.
     *
     * @param course the initialized course, if present
     * @return the narrow course DTO, or {@code null} when no course is present
     */
    public static @Nullable ExerciseDetailsCourseDTO of(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new ExerciseDetailsCourseDTO(course.getId(), course.getTitle(), course.getShortName(), course.getStudentGroupName(), course.getTeachingAssistantGroupName(),
                course.getEditorGroupName(), course.getInstructorGroupName(), course.getAccuracyOfScores(), course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled(),
                course.getCourseInformationSharingConfiguration());
    }
}
