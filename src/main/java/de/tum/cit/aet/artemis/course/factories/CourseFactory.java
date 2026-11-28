package de.tum.cit.aet.artemis.course.factories;

import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Factory for constructing {@link Course} objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the {@code demo} profile.
 * <p>
 * This factory only <b>constructs</b> the entity, it never persists it and it never derives values on its own: every identifying value (title, short name) is passed in by the
 * caller. Tests pass randomized values to keep fixtures independent of each other, while the demo seeding uses fixed values so that re-running it stays idempotent.
 */
public final class CourseFactory {

    private CourseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a course with the passed values.
     *
     * @param title                         The title of the course.
     * @param shortName                     The short name of the course. Must start with a letter.
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
    public static Course generateCourse(String title, String shortName, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, Integer maxComplaints,
            Integer maxTeamComplaints, Integer maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean communicationEnabled,
            boolean messagingEnabled, int requestMoreFeedbackTimeDays) {
        Course course = new Course();
        course.setTitle(title);
        course.setShortName(shortName);
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
        course.setMaxComplaintTextLimit(maxComplaintTextLimit);
        course.setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit);
        course.setCourseInformationSharingConfiguration(informationSharingConfiguration(communicationEnabled, messagingEnabled));
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

    private static CourseInformationSharingConfiguration informationSharingConfiguration(boolean communicationEnabled, boolean messagingEnabled) {
        if (communicationEnabled && messagingEnabled) {
            return CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING;
        }
        if (communicationEnabled) {
            return CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        }
        return CourseInformationSharingConfiguration.DISABLED;
    }
}
