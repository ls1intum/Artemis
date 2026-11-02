package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/** Scalar settings carried by both course creation and update requests. */
public interface CourseSettingsDTO {

    String title();

    String shortName();

    String description();

    String semester();

    ZonedDateTime startDate();

    ZonedDateTime endDate();

    ZonedDateTime enrollmentStartDate();

    ZonedDateTime enrollmentEndDate();

    ZonedDateTime unenrollmentEndDate();

    boolean testCourse();

    Boolean onlineCourse();

    Language language();

    ProgrammingLanguage defaultProgrammingLanguage();

    Integer maxComplaints();

    Integer maxTeamComplaints();

    int maxComplaintTimeDays();

    int maxRequestMoreFeedbackTimeDays();

    int maxComplaintTextLimit();

    int maxComplaintResponseTextLimit();

    String color();

    Boolean enrollmentEnabled();

    String enrollmentConfirmationMessage();

    boolean unenrollmentEnabled();

    boolean learningPathsEnabled();

    Integer presentationScore();

    Integer maxPoints();

    Integer accuracyOfScores();

    String timeZone();

    CourseInformationSharingConfiguration courseInformationSharingConfiguration();

    /**
     * Applies basic information, dates, complaint limits and color.
     *
     * @param course course to update
     */
    default void applyBasicSettingsTo(Course course) {
        course.setTitle(title());
        course.setShortName(shortName());
        course.setDescription(description());
        course.setSemester(semester());
        course.setStartDate(startDate());
        course.setEndDate(endDate());
        course.setEnrollmentStartDate(enrollmentStartDate());
        course.setEnrollmentEndDate(enrollmentEndDate());
        course.setUnenrollmentEndDate(unenrollmentEndDate());
        course.setTestCourse(testCourse());
        course.setOnlineCourse(onlineCourse() != null && onlineCourse());
        course.setLanguage(language());
        course.setDefaultProgrammingLanguage(defaultProgrammingLanguage());
        course.setMaxComplaints(maxComplaints());
        course.setMaxTeamComplaints(maxTeamComplaints());
        course.setMaxComplaintTimeDays(maxComplaintTimeDays());
        course.setMaxRequestMoreFeedbackTimeDays(maxRequestMoreFeedbackTimeDays());
        course.setMaxComplaintTextLimit(maxComplaintTextLimit());
        course.setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit());
        course.setColor(color());
    }

    /**
     * Applies enrollment and unenrollment settings.
     *
     * @param course course to update
     */
    default void applyEnrollmentSettingsTo(Course course) {
        course.setEnrollmentEnabled(enrollmentEnabled());
        course.setEnrollmentConfirmationMessage(enrollmentConfirmationMessage());
        course.setUnenrollmentEnabled(unenrollmentEnabled());
    }

    /**
     * Applies shared feature, scoring and regional settings.
     *
     * @param course course to update
     */
    default void applyFeatureSettingsTo(Course course) {
        course.setLearningPathsEnabled(learningPathsEnabled());
        course.setPresentationScore(presentationScore());
        course.setMaxPoints(maxPoints());
        course.setAccuracyOfScores(accuracyOfScores());
        course.setTimeZone(timeZone());
        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration());
    }
}
