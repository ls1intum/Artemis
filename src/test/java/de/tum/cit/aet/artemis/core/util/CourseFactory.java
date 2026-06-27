package de.tum.cit.aet.artemis.core.util;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.programming.util.ShortNameGenerator;

/**
 * Factory for creating Courses and related objects.
 */
public class CourseFactory {

    /**
     * Generates a course with the passed id, start and end date, and exercises.
     * Group name columns are populated with course-derived defaults (columns remain for
     * legacy compatibility; will be dropped in a later migration phase).
     * Messaging is disabled; communication-only mode is active.
     *
     * @param id        The id of the course.
     * @param startDate The start date of the course.
     * @param endDate   The end date of the course.
     * @param exercises The course exercises.
     * @return The generated course.
     */
    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        return generateCourse(id, "short", startDate, endDate, exercises, 3, 3, 7, 2000, 2000, true, false, 7);
    }

    /**
     * Generates a course with the passed values, controlling whether messaging is enabled.
     *
     * @param id               The id of the course.
     * @param startDate        The start date of the course.
     * @param endDate          The end date of the course.
     * @param exercises        The course exercises.
     * @param messagingEnabled Whether messaging in the course should be enabled.
     * @return The generated course.
     */
    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, boolean messagingEnabled) {
        return generateCourse(id, "short", startDate, endDate, exercises, 3, 3, 7, 2000, 2000, true, messagingEnabled, 7);
    }

    /**
     * Generates a course with a custom short name.
     * Communication and messaging are both enabled by default.
     *
     * @param id        The id of the course.
     * @param shortName The short name prefix for the course.
     * @param startDate The start date of the course.
     * @param endDate   The end date of the course.
     * @param exercises The course exercises.
     * @return The generated course.
     */
    public static Course generateCourse(Long id, String shortName, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        return generateCourse(id, shortName, startDate, endDate, exercises, 3, 3, 7, 2000, 2000, true, true, 7);
    }

    /**
     * Generates a course with the passed values.
     *
     * @param id                            The id of the course.
     * @param startDate                     The start date of the course.
     * @param endDate                       The end date of the course.
     * @param exercises                     The course exercises.
     * @param maxComplaints                 The max number of allowed complaints.
     * @param maxTeamComplaints             The max number of allowed team complaints.
     * @param maxComplaintTimeDays          The max complaint time in days.
     * @param maxComplaintTextLimit         The max complaint text limit.
     * @param maxComplaintResponseTextLimit The max complaint response text limit.
     * @param communicationEnabled          Whether the communication in the course should be enabled (true) or not (false).
     * @param messagingEnabled              Whether messaging in the course should be enabled (true) or not (false).
     * @param requestMoreFeedbackTimeDays   The time to request more feedback in days.
     * @return The generated course.
     */
    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, Integer maxComplaints, Integer maxTeamComplaints,
            Integer maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean communicationEnabled, boolean messagingEnabled,
            int requestMoreFeedbackTimeDays) {
        return generateCourse(id, "short", startDate, endDate, exercises, maxComplaints, maxTeamComplaints, maxComplaintTimeDays, maxComplaintTextLimit,
                maxComplaintResponseTextLimit, communicationEnabled, messagingEnabled, requestMoreFeedbackTimeDays);
    }

    /**
     * Generates a course with the passed values.
     *
     * @param id                            The id of the course.
     * @param shortName                     The short name of the course.
     * @param startDate                     The start date of the course.
     * @param endDate                       The end date of the course.
     * @param exercises                     The course exercises.
     * @param maxComplaints                 The max number of allowed complaints.
     * @param maxTeamComplaints             The max number of allowed team complaints.
     * @param maxComplaintTimeDays          The max complaint time in days.
     * @param maxComplaintTextLimit         The max complaint text limit.
     * @param maxComplaintResponseTextLimit The max complaint response text limit.
     * @param communicationEnabled          Whether the communication in the course should be enabled (true) or not (false).
     * @param messagingEnabled              Whether messaging in the course should be enabled (true) or not (false).
     * @param requestMoreFeedbackTimeDays   The time to request more feedback in days.
     * @return The generated course.
     */
    public static Course generateCourse(Long id, String shortName, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, Integer maxComplaints,
            Integer maxTeamComplaints, Integer maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean communicationEnabled,
            boolean messagingEnabled, int requestMoreFeedbackTimeDays) {
        Course course = new Course();
        course.setId(id);

        String randomName = ShortNameGenerator.generateRandomShortName(8);

        course.setTitle("Course title " + randomName);

        // must start with a letter
        course.setShortName(shortName + randomName);
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
        course.setMaxComplaintTextLimit(maxComplaintTextLimit);
        course.setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit);
        if (communicationEnabled && messagingEnabled) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        }
        else if (communicationEnabled) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
        }
        else {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        }
        course.setMaxRequestMoreFeedbackTimeDays(requestMoreFeedbackTimeDays);
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setExercises(exercises);
        course.setOnlineCourse(false);
        course.setEnrollmentEnabled(false);
        course.setPresentationScore(2);
        course.setAccuracyOfScores(1);
        return course;
    }

    /**
     * Generates an online course configuration.
     *
     * @param course      The course for which an online configuration is to be generated.
     * @param userPrefix  The prefix of user groups in the course.
     * @param originalUrl The original url of the configuration.
     * @return the newly generated online course configuration.
     */
    public static OnlineCourseConfiguration generateOnlineCourseConfiguration(Course course, String userPrefix, String originalUrl) {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        updateOnlineCourseConfiguration(onlineCourseConfiguration, userPrefix, originalUrl, UUID.randomUUID().toString());
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        return onlineCourseConfiguration;
    }

    /**
     * Updates the online course configuration.
     *
     * @param onlineCourseConfiguration The online course configuration to be updated..
     * @param userPrefix                The prefix of user groups in the course.
     * @param originalUrl               The original url of the configuration.
     * @param registrationId            The registration id of the configuration.
     */
    public static void updateOnlineCourseConfiguration(OnlineCourseConfiguration onlineCourseConfiguration, String userPrefix, String originalUrl, String registrationId) {
        onlineCourseConfiguration.setUserPrefix(userPrefix);

        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setTokenUri(originalUrl);
        ltiPlatformConfiguration.setRegistrationId(registrationId);
    }
}
