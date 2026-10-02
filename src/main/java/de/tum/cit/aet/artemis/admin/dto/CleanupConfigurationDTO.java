package de.tum.cit.aet.artemis.admin.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;

/**
 * The effective retention cutoffs of the age-based cleanup operations, so the admin UI can state which data an
 * operation without an admin-picked date range would touch. Every cutoff carries both the configured period (from
 * {@link DataCleanupProperties}) and the concrete point in time the operation would use if it ran now, computed from a
 * single {@code now} so that all cutoffs of one response share the same reference instant.
 *
 * @param gradeRelevantRetentionYears             configured retention of grade-relevant courses, in years
 * @param gradeRelevantCoursesEndedBefore         grade-relevant courses that ended before this are due for a reset
 * @param nonGradeRelevantRetentionYears          configured retention of non-grade-relevant courses, in years
 * @param nonGradeRelevantCoursesEndedBefore      non-grade-relevant courses that ended before this are due for a reset
 * @param resetWarningGracePeriodDays             configured grace period between warning instructors and resetting, in days
 * @param coursesWarnedBefore                     courses whose instructors were warned before this are due for a reset
 * @param oldFeedbackCutoffWeeks                  configured cutoff for the feedback of non-latest results, in weeks
 * @param oldFeedbackCoursesEndedBefore           courses that ended before this have the feedback of non-latest results purged
 * @param oldSubmissionVersionsCutoffWeeks        configured cutoff for submission versions, in weeks
 * @param oldSubmissionVersionsCoursesEndedBefore courses that ended before this have their submission versions purged
 * @param notEnrolledUsersInactivityMonths        configured inactivity period before a not-enrolled user is warned, in months
 * @param usersInactiveBefore                     not-enrolled users last active before this are warned; the later deletion
 *                                                    instead compares each user's last login against their own warning date
 * @param notEnrolledUsersWarningGracePeriodDays  configured grace period between warning a user and deleting the account, in days
 * @param usersWarnedBefore                       warned users warned before this are due for deletion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CleanupConfigurationDTO(int gradeRelevantRetentionYears, ZonedDateTime gradeRelevantCoursesEndedBefore, int nonGradeRelevantRetentionYears,
        ZonedDateTime nonGradeRelevantCoursesEndedBefore, int resetWarningGracePeriodDays, ZonedDateTime coursesWarnedBefore, int oldFeedbackCutoffWeeks,
        ZonedDateTime oldFeedbackCoursesEndedBefore, int oldSubmissionVersionsCutoffWeeks, ZonedDateTime oldSubmissionVersionsCoursesEndedBefore,
        int notEnrolledUsersInactivityMonths, ZonedDateTime usersInactiveBefore, int notEnrolledUsersWarningGracePeriodDays, ZonedDateTime usersWarnedBefore) {

    /**
     * Derives the effective cutoffs from the configured periods.
     *
     * @param properties the configured cleanup periods
     * @param now        the reference instant all cutoffs are measured back from
     * @return the effective cleanup configuration
     */
    public static CleanupConfigurationDTO of(DataCleanupProperties properties, ZonedDateTime now) {
        return new CleanupConfigurationDTO(properties.gradeRelevantRetentionYears(), now.minusYears(properties.gradeRelevantRetentionYears()),
                properties.nonGradeRelevantRetentionYears(), now.minusYears(properties.nonGradeRelevantRetentionYears()), properties.resetWarningGracePeriodDays(),
                now.minusDays(properties.resetWarningGracePeriodDays()), properties.oldFeedbackCutoffWeeks(), now.minusWeeks(properties.oldFeedbackCutoffWeeks()),
                properties.oldSubmissionVersionsCutoffWeeks(), now.minusWeeks(properties.oldSubmissionVersionsCutoffWeeks()), properties.notEnrolledUsersInactivityMonths(),
                now.minusMonths(properties.notEnrolledUsersInactivityMonths()), properties.notEnrolledUsersWarningGracePeriodDays(),
                now.minusDays(properties.notEnrolledUsersWarningGracePeriodDays()));
    }
}
