package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Light, cycle-free course projection embedded in programming-exercise responses.
 * <p>
 * The unchanged Angular client reads {@code exercise.course} for display links, for access rights (the four group
 * names), for course-level complaint configuration and, on the programming grading form, for
 * {@code course.presentationScore}. It carries every component of {@link CourseForQuizExerciseDTO} plus
 * {@code presentationScore}, which that shared record does not have and which must not be added to it.
 *
 * @param id                                    the course id
 * @param title                                 the course title
 * @param description                           the course description
 * @param shortName                             the course short name used in API paths
 * @param studentGroupName                      the student group name (access rights)
 * @param teachingAssistantGroupName            the tutor group name (access rights)
 * @param editorGroupName                       the editor group name (access rights)
 * @param instructorGroupName                   the instructor group name (access rights)
 * @param startDate                             when the course starts
 * @param endDate                               when the course ends
 * @param enrollmentStartDate                   when enrollment opens
 * @param enrollmentEndDate                     when enrollment closes
 * @param unenrollmentEndDate                   when unenrollment closes
 * @param semester                              the semester the course belongs to
 * @param testCourse                            whether this is a test course
 * @param language                              the course language
 * @param defaultProgrammingLanguage            the default programming language of new exercises
 * @param onlineCourse                          whether this is an online course
 * @param courseInformationSharingConfiguration the communication/messaging configuration
 * @param maxComplaints                         the maximum number of complaints per student
 * @param maxTeamComplaints                     the maximum number of complaints per team
 * @param maxComplaintTimeDays                  the complaint window in days
 * @param maxRequestMoreFeedbackTimeDays        the more-feedback-request window in days
 * @param maxComplaintTextLimit                 the maximum complaint text length
 * @param maxComplaintResponseTextLimit         the maximum complaint response text length
 * @param complaintsEnabled                     whether complaints are enabled
 * @param requestMoreFeedbackEnabled            whether more-feedback requests are enabled
 * @param accuracyOfScores                      the number of decimal places used for scores
 * @param presentationScore                     the course presentation score (read by the grading form)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseCourseDTO(Long id, String title, String description, String shortName, String studentGroupName, String teachingAssistantGroupName,
        String editorGroupName, String instructorGroupName, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate,
        ZonedDateTime unenrollmentEndDate, String semester, boolean testCourse, Language language, ProgrammingLanguage defaultProgrammingLanguage, Boolean onlineCourse,
        CourseInformationSharingConfiguration courseInformationSharingConfiguration, Integer maxComplaints, Integer maxTeamComplaints, int maxComplaintTimeDays,
        int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean complaintsEnabled, boolean requestMoreFeedbackEnabled,
        Integer accuracyOfScores, Integer presentationScore) implements Serializable {

    /**
     * Creates a {@link ProgrammingExerciseCourseDTO} from a {@link Course}.
     *
     * @param course the course to project (may be {@code null})
     * @return the projected course, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseCourseDTO of(Course course) {
        if (course == null) {
            return null;
        }
        return new ProgrammingExerciseCourseDTO(course.getId(), course.getTitle(), course.getDescription(), course.getShortName(), course.getStudentGroupName(),
                course.getTeachingAssistantGroupName(), course.getEditorGroupName(), course.getInstructorGroupName(), course.getStartDate(), course.getEndDate(),
                course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.getSemester(), course.isTestCourse(), course.getLanguage(),
                course.getDefaultProgrammingLanguage(), course.isOnlineCourse(), course.getCourseInformationSharingConfiguration(), course.getMaxComplaints(),
                course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(), course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(),
                course.getMaxComplaintResponseTextLimit(), course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled(), course.getAccuracyOfScores(),
                course.getPresentationScore());
    }
}
