package de.tum.cit.aet.artemis.admin.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the admin data-privacy cleanup jobs (GDPR retention), bound from {@code artemis.cleanup}. Cutoff
 * defaults follow German/EU data-protection practice (GDPR Art. 5(1)(e) storage limitation and Art. 17 erasure): the
 * grade-relevant default (5 years) matches the examination-document retention for Bavarian universities, after which the
 * data must be destroyed; non-grade-relevant courses carry no exam-retention obligation and are minimized after 1 year.
 * The authoritative long-term grade/degree record is kept in the campus-management system (e.g. TUMonline), not Artemis.
 * All defaults are configurable per institution. The cron expressions for the scheduled variants live under
 * {@code artemis.scheduling}; the {@code *ScheduleEnabled} flags here are the kill switches and default to {@code false}
 * so nothing runs automatically until an admin opts in.
 *
 * @param gradeRelevantRetentionYears            Years after a grade-relevant course ends before its student data is reset
 *                                                   (exam/grade records retention).
 * @param nonGradeRelevantRetentionYears         Years after a non-grade-relevant course ends before its student data is reset
 *                                                   (data minimization).
 * @param resetWarningGracePeriodDays            Days between warning instructors (archive + email) and actually resetting the
 *                                                   student data, giving them time to download their backup.
 * @param oldFeedbackCutoffWeeks                 Weeks after a course ends before feedback of non-latest results is purged (only
 *                                                   the latest rated and latest non-rated result's feedback is kept).
 * @param oldSubmissionVersionsCutoffWeeks       Weeks after a course ends before its submission versions (editor keystroke
 *                                                   history) are purged.
 * @param notEnrolledUsersInactivityMonths       Months of inactivity (by last login, falling back to creation date) required
 *                                                   before a user enrolled in no course is warned and later permanently deleted when no domain reference blocks it.
 * @param notEnrolledUsersWarningGracePeriodDays Days between warning a not-enrolled, inactive user and actually deleting the
 *                                                   account, giving them time to log in (which cancels the deletion).
 * @param oldCoursesWarningScheduleEnabled       Whether the scheduled "warn + archive old courses" job runs automatically.
 * @param oldCoursesResetScheduleEnabled         Whether the scheduled "reset old courses' student data" job runs automatically.
 * @param oldFeedbackScheduleEnabled             Whether the scheduled "delete feedback of non-latest results" job runs automatically.
 * @param oldSubmissionVersionsScheduleEnabled   Whether the scheduled "delete old submission versions" job runs automatically.
 * @param notEnrolledUsersWarningScheduleEnabled Whether the scheduled "warn not-enrolled users" job runs automatically.
 * @param notEnrolledUsersScheduleEnabled        Whether the scheduled "delete not-enrolled users" job runs automatically.
 * @param plagiarismCasesScheduleEnabled         Whether the scheduled "delete plagiarism cases of old courses" job runs automatically.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.cleanup", ignoreUnknownFields = false)
public record DataCleanupProperties(@DefaultValue("5") @Positive int gradeRelevantRetentionYears, @DefaultValue("1") @Positive int nonGradeRelevantRetentionYears,
        @DefaultValue("30") @Positive int resetWarningGracePeriodDays, @DefaultValue("8") @Positive int oldFeedbackCutoffWeeks,
        @DefaultValue("8") @Positive int oldSubmissionVersionsCutoffWeeks, @DefaultValue("6") @Positive int notEnrolledUsersInactivityMonths,
        @DefaultValue("30") @Positive int notEnrolledUsersWarningGracePeriodDays, @DefaultValue("false") boolean oldCoursesWarningScheduleEnabled,
        @DefaultValue("false") boolean oldCoursesResetScheduleEnabled, @DefaultValue("false") boolean oldFeedbackScheduleEnabled,
        @DefaultValue("false") boolean oldSubmissionVersionsScheduleEnabled, @DefaultValue("false") boolean notEnrolledUsersWarningScheduleEnabled,
        @DefaultValue("false") boolean notEnrolledUsersScheduleEnabled, @DefaultValue("false") boolean plagiarismCasesScheduleEnabled) {
}
