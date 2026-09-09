package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

/**
 * DTO-safe course management response containing scalar settings and explicitly initialized configurations.
 *
 * @param id                                             the course identifier
 * @param title                                          the course title
 * @param shortName                                      the unique course short name
 * @param description                                    the optional description
 * @param semester                                       the optional semester
 * @param startDate                                      the optional course start
 * @param endDate                                        the optional course end
 * @param enrollmentStartDate                            the optional enrollment start
 * @param enrollmentEndDate                              the optional enrollment end
 * @param unenrollmentEndDate                            the optional unenrollment end
 * @param testCourse                                     whether this is a test course
 * @param language                                       the optional course language
 * @param defaultProgrammingLanguage                     the optional default programming language
 * @param timeZone                                       the optional course time zone
 * @param color                                          the optional display color
 * @param courseIcon                                     the optional course icon
 * @param enrollmentEnabled                              whether self-enrollment is enabled, when configured
 * @param unenrollmentEnabled                            whether self-unenrollment is enabled
 * @param enrollmentConfirmationMessage                  the optional enrollment confirmation message
 * @param onboardingDone                                 whether onboarding has been completed
 * @param onlineCourse                                   whether this is an online course
 * @param courseInformationSharingConfiguration          the optional communication configuration
 * @param courseInformationSharingMessagingCodeOfConduct the optional communication code of conduct
 * @param maxComplaints                                  the optional complaint limit
 * @param maxTeamComplaints                              the optional team complaint limit
 * @param maxComplaintTimeDays                           the complaint time limit
 * @param maxRequestMoreFeedbackTimeDays                 the feedback-request time limit
 * @param maxComplaintTextLimit                          the complaint text limit
 * @param maxComplaintResponseTextLimit                  the complaint-response text limit
 * @param presentationScore                              the optional presentation score
 * @param maxPoints                                      the optional course maximum points
 * @param accuracyOfScores                               the optional score accuracy
 * @param complaintsEnabled                              whether complaints are enabled
 * @param requestMoreFeedbackEnabled                     whether feedback requests are enabled
 * @param athenaGradingFeedbackEnabled                   whether Athena grading feedback is enabled
 * @param athenaFormativeFeedbackEnabled                 whether Athena formative feedback is enabled
 * @param learningPathsEnabled                           whether learning paths are enabled
 * @param trainingEnabled                                whether training mode is enabled
 * @param numberOfStudents                               the optional student count
 * @param numberOfTeachingAssistants                     the optional teaching-assistant count
 * @param numberOfEditors                                the optional editor count
 * @param numberOfInstructors                            the optional instructor count
 * @param onlineCourseConfiguration                      the optional initialized online-course configuration
 * @param tutorialGroupsConfiguration                    the optional initialized tutorial-group configuration
 * @param courseConfiguration                            the optional initialized course-level configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementDTO(long id, String title, String shortName, @Nullable String description, @Nullable String semester, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable ZonedDateTime enrollmentStartDate, @Nullable ZonedDateTime enrollmentEndDate, @Nullable ZonedDateTime unenrollmentEndDate,
        boolean testCourse, @Nullable Language language, @Nullable ProgrammingLanguage defaultProgrammingLanguage, @Nullable String timeZone, @Nullable String color,
        @Nullable String courseIcon, @Nullable Boolean enrollmentEnabled, boolean unenrollmentEnabled, @Nullable String enrollmentConfirmationMessage, boolean onboardingDone,
        boolean onlineCourse, @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration,
        @Nullable String courseInformationSharingMessagingCodeOfConduct, @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays,
        int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, @Nullable Integer presentationScore, @Nullable Integer maxPoints,
        @Nullable Integer accuracyOfScores, boolean complaintsEnabled, boolean requestMoreFeedbackEnabled, boolean athenaGradingFeedbackEnabled,
        boolean athenaFormativeFeedbackEnabled, boolean learningPathsEnabled, boolean trainingEnabled, @Nullable Long numberOfStudents, @Nullable Long numberOfTeachingAssistants,
        @Nullable Long numberOfEditors, @Nullable Long numberOfInstructors, @Nullable OnlineCourseConfigurationResponseDTO onlineCourseConfiguration,
        @Nullable TutorialGroupsConfigurationResponseDTO tutorialGroupsConfiguration, @Nullable CourseConfigurationResponseDTO courseConfiguration) {

    /**
     * Maps a course without traversing any collection or uninitialized configuration association.
     *
     * @param course the authorized course to map
     * @return the course management response
     */
    public static CourseManagementDTO of(Course course) {
        OnlineCourseConfiguration onlineConfiguration = course.getOnlineCourseConfiguration();
        TutorialGroupsConfiguration tutorialConfiguration = course.getTutorialGroupsConfiguration();
        CourseConfiguration configuration = course.getCourseConfiguration();
        return new CourseManagementDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription(), course.getSemester(), course.getStartDate(),
                course.getEndDate(), course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.isTestCourse(), course.getLanguage(),
                course.getDefaultProgrammingLanguage(), course.getTimeZone(), course.getColor(), course.getCourseIcon(), course.isEnrollmentEnabled(),
                course.isUnenrollmentEnabled(), course.getEnrollmentConfirmationMessage(), course.isOnboardingDone(), course.isOnlineCourse(),
                course.getCourseInformationSharingConfiguration(), course.getCourseInformationSharingMessagingCodeOfConduct(), course.getMaxComplaints(),
                course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(), course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(),
                course.getMaxComplaintResponseTextLimit(), course.getPresentationScore(), course.getMaxPoints(), course.getAccuracyOfScores(), course.getComplaintsEnabled(),
                course.getRequestMoreFeedbackEnabled(), course.isAthenaGradingFeedbackEnabled(), course.isAthenaFormativeFeedbackEnabled(), course.getLearningPathsEnabled(),
                course.isTrainingEnabled(), course.getNumberOfStudents(), course.getNumberOfTeachingAssistants(), course.getNumberOfEditors(), course.getNumberOfInstructors(),
                onlineConfiguration == null ? null : OnlineCourseConfigurationResponseDTO.of(onlineConfiguration),
                tutorialConfiguration != null && Hibernate.isInitialized(tutorialConfiguration) ? TutorialGroupsConfigurationResponseDTO.of(tutorialConfiguration) : null,
                configuration == null ? null : CourseConfigurationResponseDTO.of(configuration));
    }
}
