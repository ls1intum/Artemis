package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

/**
 * The course data the course overview container itself needs.
 * <p>
 * Deliberately scalars rather than the {@code Course} entity. Returning the entity made the response whatever Jackson
 * happened to serialize off it, which meant the contract moved whenever the entity did, and it left the door open for a
 * lazy association to be initialised on the way out. Every field here is one the web client reads.
 * <p>
 * Content collections are absent: exercises, lectures, exams, participations and scores are loaded by the tab that
 * needs them, and which tabs to offer comes from {@link CourseAvailableTabsDTO}. {@code tutorialGroupsConfiguration} is
 * absent for the same reason — it was never initialised on this path either, and the tutorial groups tab loads its own.
 *
 * @param id                                             the id of the course
 * @param title                                          the title shown in the header and the browser tab
 * @param shortName                                      the short name, used in links and repository names
 * @param description                                    the course description
 * @param semester                                       the semester the course belongs to
 * @param startDate                                      when the course starts
 * @param endDate                                        when the course ends; drives the archived/active distinction
 * @param enrollmentEnabled                              whether students may enroll themselves
 * @param enrollmentStartDate                            when self-enrollment opens
 * @param enrollmentEndDate                              when self-enrollment closes
 * @param enrollmentConfirmationMessage                  shown when a student enrolls
 * @param unenrollmentEnabled                            whether students may unenroll themselves
 * @param unenrollmentEndDate                            until when they may
 * @param color                                          the course colour used across the overview
 * @param courseIcon                                     the path of the course icon
 * @param testCourse                                     whether this is a test course
 * @param onlineCourse                                   whether this is an LTI online course
 * @param language                                       the course language
 * @param timeZone                                       the course time zone, used for tutorial groups and the calendar
 * @param courseInformationSharingConfiguration          which communication features are enabled
 * @param courseInformationSharingMessagingCodeOfConduct the code of conduct shown in communication
 * @param maxPoints                                      the points achievable in the course
 * @param accuracyOfScores                               the number of decimal places scores are rounded to
 * @param presentationScore                              the basic presentation threshold, or {@code null} when unused
 * @param maxComplaints                                  how many complaints a student may file
 * @param maxTeamComplaints                              how many complaints a team may file
 * @param maxComplaintTimeDays                           how long a complaint may be filed after the result
 * @param maxComplaintTextLimit                          the character limit of a complaint
 * @param maxComplaintResponseTextLimit                  the character limit of a complaint response
 * @param maxRequestMoreFeedbackTimeDays                 how long more feedback may be requested after the result
 * @param complaintsEnabled                              derived: whether complaints are possible at all
 * @param requestMoreFeedbackEnabled                     derived: whether more feedback may be requested at all
 * @param learningPathsEnabled                           whether the learning path tab may be offered
 * @param trainingEnabled                                whether the course has quiz questions available for practice
 * @param gradeRelevant                                  derived: whether the course counts towards a grade
 * @param dataRetentionHold                              derived: whether the course is under a data retention hold
 * @param restrictedAthenaModulesAccess                  whether Athena modules are restricted for this course
 * @param onboardingDone                                 whether the instructor onboarding was completed
 * @param courseNotificationCount                        the number of unread notifications for the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForOverviewDTO(long id, String title, @Nullable String shortName, @Nullable String description, @Nullable String semester, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable Boolean enrollmentEnabled, @Nullable ZonedDateTime enrollmentStartDate, @Nullable ZonedDateTime enrollmentEndDate,
        @Nullable String enrollmentConfirmationMessage, boolean unenrollmentEnabled, @Nullable ZonedDateTime unenrollmentEndDate, @Nullable String color,
        @Nullable String courseIcon, boolean testCourse, @Nullable Boolean onlineCourse, @Nullable Language language, @Nullable String timeZone,
        @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration, @Nullable String courseInformationSharingMessagingCodeOfConduct,
        @Nullable Integer maxPoints, @Nullable Integer accuracyOfScores, @Nullable Integer presentationScore, @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints,
        int maxComplaintTimeDays, int maxComplaintTextLimit, int maxComplaintResponseTextLimit, int maxRequestMoreFeedbackTimeDays, boolean complaintsEnabled,
        boolean requestMoreFeedbackEnabled, boolean learningPathsEnabled, boolean trainingEnabled, boolean gradeRelevant, boolean dataRetentionHold,
        boolean restrictedAthenaModulesAccess, boolean onboardingDone, long courseNotificationCount) {

    /**
     * Reads the overview fields off a course.
     *
     * @param course                  the course the overview is opened for
     * @param courseNotificationCount the number of unread notifications for it
     * @return the projected course
     */
    public static CourseForOverviewDTO of(Course course, long courseNotificationCount) {
        return new CourseForOverviewDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription(), course.getSemester(), course.getStartDate(),
                course.getEndDate(), course.isEnrollmentEnabled(), course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getEnrollmentConfirmationMessage(),
                course.isUnenrollmentEnabled(), course.getUnenrollmentEndDate(), course.getColor(), course.getCourseIcon(), course.isTestCourse(), course.isOnlineCourse(),
                course.getLanguage(), course.getTimeZone(), course.getCourseInformationSharingConfiguration(), course.getCourseInformationSharingMessagingCodeOfConduct(),
                course.getMaxPoints(), course.getAccuracyOfScores(), course.getPresentationScore(), course.getMaxComplaints(), course.getMaxTeamComplaints(),
                course.getMaxComplaintTimeDays(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(), course.getMaxRequestMoreFeedbackTimeDays(),
                course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled(), course.getLearningPathsEnabled(), course.isTrainingEnabled(), course.isGradeRelevant(),
                course.isDataRetentionHold(), course.getRestrictedAthenaModulesAccess(), course.isOnboardingDone(), courseNotificationCount);
    }
}
