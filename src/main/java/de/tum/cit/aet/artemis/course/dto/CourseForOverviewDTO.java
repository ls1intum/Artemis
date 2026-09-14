package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

/**
 * The course data the course overview container itself needs.
 * <p>
 * Scalars rather than the {@code Course} entity, and projected straight out of the database: the endpoint never
 * materialises a Course on the successful path, so the response cannot drift with the entity and no lazy association
 * can be initialised on the way out.
 * <p>
 * Every field here has a reader on an overview path. Fields the entity carries but the overview never reads are
 * deliberately absent — short name, description, semester, language, time zone, max points, the enrolment start date
 * and confirmation message, and the instructor-facing onboarding and data-retention flags. The two Athena flags stay
 * in, unlike the rest of the Athena configuration: student-facing feedback-request controls on this path gate on them.
 * {@code learningPathsEnabled} and {@code trainingEnabled} are gone too: which tabs to offer now comes from
 * {@link CourseAvailableTabsDTO}. Content collections are absent because each tab loads what it needs, and
 * {@code tutorialGroupsConfiguration} because it was never initialised on this path either.
 *
 * @param id                                             the id of the course
 * @param title                                          shown in the header, the browser tab, and typed to confirm unenrollment
 * @param startDate                                      when the course starts
 * @param endDate                                        when the course ends; separates active from archived courses
 * @param color                                          the course colour used across the overview
 * @param courseIcon                                     the course icon, from which the client derives its display path
 * @param testCourse                                     whether this is a test course
 * @param onlineCourse                                   whether this is an LTI online course
 * @param enrollmentEnabled                              whether self-enrollment is on; the unenrollment dialog reads it
 * @param enrollmentEndDate                              when self-enrollment closes; likewise
 * @param unenrollmentEnabled                            whether the overview offers the unenroll action
 * @param unenrollmentEndDate                            until when it does
 * @param courseInformationSharingConfiguration          which communication features are enabled
 * @param courseInformationSharingMessagingCodeOfConduct the code of conduct shown in the communication tab
 * @param accuracyOfScores                               the number of decimal places scores are rounded to
 * @param presentationScore                              the basic presentation threshold, read by the statistics tab
 * @param complaintsEnabled                              whether complaints are possible at all
 * @param maxComplaints                                  how many complaints a student may file
 * @param maxTeamComplaints                              how many complaints a team may file
 * @param maxComplaintTimeDays                           how long a complaint may be filed after the result
 * @param maxComplaintTextLimit                          the character limit of a complaint
 * @param maxComplaintResponseTextLimit                  the character limit of a complaint response
 * @param requestMoreFeedbackEnabled                     whether more feedback may be requested at all
 * @param maxRequestMoreFeedbackTimeDays                 how long more feedback may be requested after the result
 * @param athenaGradingFeedbackEnabled                   whether course-level Athena grading feedback is enabled
 * @param athenaFormativeFeedbackEnabled                 whether course-level Athena formative feedback is enabled
 * @param courseNotificationCount                        the number of unread notifications for the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForOverviewDTO(long id, String title, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable String color, @Nullable String courseIcon,
        boolean testCourse, @Nullable Boolean onlineCourse, @Nullable Boolean enrollmentEnabled, @Nullable ZonedDateTime enrollmentEndDate, boolean unenrollmentEnabled,
        @Nullable ZonedDateTime unenrollmentEndDate, @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration,
        @Nullable String courseInformationSharingMessagingCodeOfConduct, @Nullable Integer accuracyOfScores, @Nullable Integer presentationScore, boolean complaintsEnabled,
        @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit,
        boolean requestMoreFeedbackEnabled, int maxRequestMoreFeedbackTimeDays, boolean athenaGradingFeedbackEnabled, boolean athenaFormativeFeedbackEnabled,
        long courseNotificationCount) {

    /**
     * JPQL constructor, leaving the notification count at zero for {@link #withNotificationCount(long)} to fill in.
     * Complaints and more-feedback requests are enabled by their respective day limits being
     * positive, which the entity derives in {@code getComplaintsEnabled()} and {@code getRequestMoreFeedbackEnabled()}.
     * Deriving them here keeps that rule in one place rather than repeating it in the query.
     */
    public CourseForOverviewDTO(long id, String title, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable String color, @Nullable String courseIcon,
            boolean testCourse, @Nullable Boolean onlineCourse, @Nullable Boolean enrollmentEnabled, @Nullable ZonedDateTime enrollmentEndDate, boolean unenrollmentEnabled,
            @Nullable ZonedDateTime unenrollmentEndDate, @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration,
            @Nullable String courseInformationSharingMessagingCodeOfConduct, @Nullable Integer accuracyOfScores, @Nullable Integer presentationScore,
            @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit,
            int maxRequestMoreFeedbackTimeDays, boolean athenaGradingFeedbackEnabled, boolean athenaFormativeFeedbackEnabled) {
        this(id, title, startDate, endDate, color, courseIcon, testCourse, onlineCourse, enrollmentEnabled, enrollmentEndDate, unenrollmentEnabled, unenrollmentEndDate,
                courseInformationSharingConfiguration, courseInformationSharingMessagingCodeOfConduct, accuracyOfScores, presentationScore, maxComplaintTimeDays > 0, maxComplaints,
                maxTeamComplaints, maxComplaintTimeDays, maxComplaintTextLimit, maxComplaintResponseTextLimit, maxRequestMoreFeedbackTimeDays > 0, maxRequestMoreFeedbackTimeDays,
                athenaGradingFeedbackEnabled, athenaFormativeFeedbackEnabled, 0);
    }

    /**
     * Returns a copy carrying the given unread notification count.
     *
     * The count comes from a different table than the projection, so the query leaves it at zero and the caller fills
     * it in.
     *
     * @param courseNotificationCount the number of unread notifications for the course
     * @return the projected course with its notification count
     */
    public CourseForOverviewDTO withNotificationCount(long courseNotificationCount) {
        return new CourseForOverviewDTO(id, title, startDate, endDate, color, courseIcon, testCourse, onlineCourse, enrollmentEnabled, enrollmentEndDate, unenrollmentEnabled,
                unenrollmentEndDate, courseInformationSharingConfiguration, courseInformationSharingMessagingCodeOfConduct, accuracyOfScores, presentationScore, complaintsEnabled,
                maxComplaints, maxTeamComplaints, maxComplaintTimeDays, maxComplaintTextLimit, maxComplaintResponseTextLimit, requestMoreFeedbackEnabled,
                maxRequestMoreFeedbackTimeDays, athenaGradingFeedbackEnabled, athenaFormativeFeedbackEnabled, courseNotificationCount);
    }
}
