package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_RESPONSE_TEXT_LIMIT;
import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_TEXT_LIMIT;
import static de.tum.cit.aet.artemis.core.config.Constants.COURSE_SHORT_NAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_GRADING_POINTS;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PRESENTATION_SCORE;
import static de.tum.cit.aet.artemis.core.config.Constants.SHORT_NAME_PATTERN;

import java.util.regex.Matcher;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Validates {@link Course} field invariants that must hold before a course is created or updated.
 */
public final class CourseValidator {

    private CourseValidator() {
        // Utility class, no instances allowed
    }

    /**
     * Validates that only one of onlineCourse and enrollmentEnabled is selected
     *
     * @param course the course to validate
     */
    public static void validateOnlineCourseAndEnrollmentEnabled(Course course) {
        if (course.isOnlineCourse() && course.isEnrollmentEnabled()) {
            throw new BadRequestAlertException("Online course and enrollment enabled cannot be active at the same time", Course.ENTITY_NAME, "onlineCourseEnrollmentEnabledInvalid",
                    true);
        }
    }

    /**
     * Validates that the accuracy of the scores is between 0 and 5
     *
     * @param course the course to validate
     */
    public static void validateAccuracyOfScores(Course course) {
        if (course.getAccuracyOfScores() == null) {
            throw new BadRequestAlertException("The course needs to specify the accuracy of scores", Course.ENTITY_NAME, "accuracyOfScoresNotSet", true);
        }
        if (course.getAccuracyOfScores() < 0 || course.getAccuracyOfScores() > 5) {
            throw new BadRequestAlertException("The accuracy of scores defined for the course is either negative or uses too many decimal places (more than five)",
                    Course.ENTITY_NAME, "accuracyOfScoresInvalid", true);
        }
    }

    /**
     * Validates that the configurable point values of the course stay within the allowed range. Both {@code maxPoints}
     * and {@code presentationScore} are optional; when set, {@code maxPoints} must be between 1 and
     * {@link de.tum.cit.aet.artemis.core.config.Constants#MAX_GRADING_POINTS} and {@code presentationScore} must be
     * between 0 (disabled) and {@link de.tum.cit.aet.artemis.core.config.Constants#MAX_PRESENTATION_SCORE}.
     *
     * @param course the course to validate
     */
    public static void validatePointBounds(Course course) {
        if (course.getMaxPoints() != null && (course.getMaxPoints() < 1 || course.getMaxPoints() > MAX_GRADING_POINTS)) {
            throw new BadRequestAlertException("The maximum number of points for the course must be between 1 and " + MAX_GRADING_POINTS, Course.ENTITY_NAME, "maxPointsInvalid",
                    true);
        }
        if (course.getPresentationScore() != null && (course.getPresentationScore() < 0 || course.getPresentationScore() > MAX_PRESENTATION_SCORE)) {
            throw new BadRequestAlertException("The presentation score for the course must be between 0 and " + MAX_PRESENTATION_SCORE, Course.ENTITY_NAME,
                    "presentationScoreInvalid", true);
        }
    }

    /**
     * Validates that the short name of the course follows SHORT_NAME_PATTERN and (for new courses) does not exceed
     * {@link de.tum.cit.aet.artemis.core.config.Constants#COURSE_SHORT_NAME_MAX_LENGTH}.
     * Course short names are immutable after creation, but the update path re-runs this validator with the persisted
     * value — so the max-length check is gated on a missing id to avoid breaking edits of legacy courses whose
     * shortName predates the limit.
     *
     * @param course the course to validate
     */
    public static void validateShortName(Course course) {
        // Check if the course shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(course.getShortName());
        if (!shortNameMatcher.matches()) {
            throw new BadRequestAlertException("The shortname is invalid", Course.ENTITY_NAME, "shortnameInvalid", true);
        }
        if (course.getId() == null && course.getShortName().length() > COURSE_SHORT_NAME_MAX_LENGTH) {
            throw new BadRequestAlertException("The shortname must not exceed " + COURSE_SHORT_NAME_MAX_LENGTH + " characters", Course.ENTITY_NAME, "shortnameTooLong", true);
        }
    }

    /**
     * validates that the configuration for complaints and more feedback requests is correct
     *
     * @param course the course to validate
     */
    public static void validateComplaintsAndRequestMoreFeedbackConfig(Course course) {
        if (course.getMaxComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            course.setMaxComplaints(3);
        }
        if (course.getMaxTeamComplaints() == null) {
            // set the default value to prevent null pointer exceptions
            course.setMaxTeamComplaints(3);
        }
        if (course.getMaxComplaints() < 0) {
            throw new BadRequestAlertException("Max Complaints cannot be negative", Course.ENTITY_NAME, "maxComplaintsInvalid", true);
        }
        if (course.getMaxTeamComplaints() < 0) {
            throw new BadRequestAlertException("Max Team Complaints cannot be negative", Course.ENTITY_NAME, "maxTeamComplaintsInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() < 0) {
            throw new BadRequestAlertException("Max Complaint Days cannot be negative", Course.ENTITY_NAME, "maxComplaintDaysInvalid", true);
        }
        if (course.getMaxComplaintTextLimit() < 0) {
            throw new BadRequestAlertException("Max Complaint text limit cannot be negative", Course.ENTITY_NAME, "maxComplaintTextLimitInvalid", true);
        }
        if (course.getMaxComplaintTextLimit() > COMPLAINT_TEXT_LIMIT) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be above " + COMPLAINT_TEXT_LIMIT + " characters.", Course.ENTITY_NAME,
                    "maxComplaintTextLimitInvalid", true);
        }
        if (course.getMaxComplaintResponseTextLimit() < 0) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be negative", Course.ENTITY_NAME, "maxComplaintResponseTextLimitInvalid", true);
        }
        if (course.getMaxComplaintResponseTextLimit() > COMPLAINT_RESPONSE_TEXT_LIMIT) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be above " + COMPLAINT_RESPONSE_TEXT_LIMIT + " characters.", Course.ENTITY_NAME,
                    "maxComplaintResponseTextLimitInvalid", true);
        }
        if (course.getMaxRequestMoreFeedbackTimeDays() < 0) {
            throw new BadRequestAlertException("Max Request More Feedback Days cannot be negative", Course.ENTITY_NAME, "maxRequestMoreFeedbackDaysInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() == 0 && (course.getMaxComplaints() != 0 || course.getMaxTeamComplaints() != 0)) {
            throw new BadRequestAlertException("If complaints or more feedback requests are allowed, the complaint time in days must be positive.", Course.ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
        if (course.getMaxComplaintTimeDays() != 0 && course.getMaxComplaints() == 0 && course.getMaxTeamComplaints() == 0) {
            throw new BadRequestAlertException("If no complaints or more feedback requests are allowed, the complaint time in days should be set to zero.", Course.ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
    }

    /**
     * Validates that the enrollment confirmation message does not exceed the maximum length.
     *
     * @param course the course to validate
     */
    public static void validateEnrollmentConfirmationMessage(Course course) {
        if (course.getEnrollmentConfirmationMessage() != null && course.getEnrollmentConfirmationMessage().length() > 2000) {
            throw new BadRequestAlertException("Confirmation enrollment message must be shorter than 2000 characters", Course.ENTITY_NAME, "confirmationEnrollmentMessageInvalid",
                    true);
        }
    }

    /**
     * Validates that the start and end dates of the course are set and in the correct order.
     * <p>
     * Both dates are mandatory. The data-protection features select courses by their end date, so a course without
     * one would never be archived, warned about or cleaned up.
     *
     * @param course the course to validate
     */
    public static void validateStartAndEndDate(Course course) {
        if (course.getStartDate() == null || course.getEndDate() == null) {
            throw new BadRequestAlertException("For Courses, both the start date and the end date are required", Course.ENTITY_NAME, "courseStartOrEndDateMissing", true);
        }
        if (!course.getStartDate().isBefore(course.getEndDate())) {
            throw new BadRequestAlertException("For Courses, the start date has to be before the end date", Course.ENTITY_NAME, "invalidCourseStartDate", true);
        }
    }

    /**
     * Validates that the semester of the course is set.
     * <p>
     * No format is enforced: installations outside TUM use other conventions, and the client only offers its date
     * auto-fill for values it recognises.
     *
     * @param course the course to validate
     */
    public static void validateSemester(Course course) {
        if (course.getSemester() == null || course.getSemester().isBlank()) {
            throw new BadRequestAlertException("For Courses, the semester is required", Course.ENTITY_NAME, "semesterMissing", true);
        }
        if (course.getSemester().length() > Course.SEMESTER_MAX_LENGTH) {
            throw new BadRequestAlertException("The semester must not be longer than " + Course.SEMESTER_MAX_LENGTH + " characters", Course.ENTITY_NAME, "semesterTooLong", true);
        }
    }

    /**
     * Validates if the start and end date to enroll in the course fulfill all requirements.
     * <p>
     * The enrollment period is considered valid if
     * <ul>
     * <li>start and end date of the course are set and valid ({@link #validateStartAndEndDate(Course)})</li>
     * <li>start and end date of the enrollment period are in the correct order,</li>
     * <li>and the start and end date of the enrollment is before the end date of the course.</li>
     * </ul>
     *
     * @param course the course to validate
     * @throws BadRequestAlertException if the enrollment period is invalid
     */
    public static void validateEnrollmentStartAndEndDate(Course course) {
        if (course.getEnrollmentStartDate() == null || course.getEnrollmentEndDate() == null) {
            return;
        }
        final String errorKey = "enrollmentPeriodInvalid";
        if (!course.getEnrollmentStartDate().isBefore(course.getEnrollmentEndDate())) {
            throw new BadRequestAlertException("Enrollment start date must be before the end date.", Course.ENTITY_NAME, errorKey, true);
        }

        // validateStartAndEndDate rejects a missing start or end date itself, so there is nothing to check here first.
        validateStartAndEndDate(course);

        if (course.getEnrollmentEndDate().isAfter(course.getEndDate())) {
            throw new BadRequestAlertException("Enrollment end can not be after the end date of the course.", Course.ENTITY_NAME, errorKey, true);
        }
    }

    /**
     * Validates if the end date to unenroll from the course fulfills all requirements.
     * <p>
     * The unenrollment end date is considered valid if
     * <ul>
     * <li>start and end date of the enrollment period are set and valid ({@link #validateEnrollmentStartAndEndDate(Course)})</li>
     * <li>the enrollment period ends before the unenrollment end date,</li>
     * <li>and the end date for unenrollment is not after the end date of the course.</li>
     * </ul>
     *
     * @param course the course to validate
     * @throws BadRequestAlertException if the unenrollment end date is invalid
     */
    public static void validateUnenrollmentEndDate(Course course) {
        if (course.getUnenrollmentEndDate() == null) {
            return;
        }

        validateEnrollmentStartAndEndDate(course);

        final String errorKey = "unenrollmentEndDateInvalid";

        if (course.getEnrollmentStartDate() == null || course.getEnrollmentEndDate() == null) {
            throw new BadRequestAlertException("Unenrollment end date requires a configured enrollment period.", Course.ENTITY_NAME, errorKey, true);
        }

        if (!course.getEnrollmentEndDate().isBefore(course.getUnenrollmentEndDate())) {
            throw new BadRequestAlertException("End date for enrollment must be before the end date to unenroll.", Course.ENTITY_NAME, errorKey, true);
        }

        if (course.getUnenrollmentEndDate().isAfter(course.getEndDate())) {
            throw new BadRequestAlertException("End date for enrollment can not be after the end date of the course.", Course.ENTITY_NAME, errorKey, true);
        }
    }
}
