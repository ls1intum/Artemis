package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// TODO: we should reduce the amount of information here, not all fields are needed for the quiz exercise
public record CourseForQuizExerciseDTO(Long id, String title, String description, String shortName, String studentGroupName, String teachingAssistantGroupName,
        String editorGroupName, String instructorGroupName, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate,
        ZonedDateTime unenrollmentEndDate, String semester, boolean testCourse, Language language, ProgrammingLanguage defaultProgrammingLanguage, Boolean onlineCourse,
        CourseInformationSharingConfiguration courseInformationSharingConfiguration, Integer maxComplaints, Integer maxTeamComplaints, int maxComplaintTimeDays,
        int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, Integer accuracyOfScores) {

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
        return new CourseForQuizExerciseDTO(course.getId(), course.getTitle(), course.getDescription(), course.getShortName(), course.getStudentGroupName(),
                course.getTeachingAssistantGroupName(), course.getEditorGroupName(), course.getInstructorGroupName(), course.getStartDate(), course.getEndDate(),
                course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.getSemester(), course.isTestCourse(), course.getLanguage(),
                course.getDefaultProgrammingLanguage(), course.isOnlineCourse(), course.getCourseInformationSharingConfiguration(), course.getMaxComplaints(),
                course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(), course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(),
                course.getMaxComplaintResponseTextLimit(), course.getAccuracyOfScores());
    }

}
