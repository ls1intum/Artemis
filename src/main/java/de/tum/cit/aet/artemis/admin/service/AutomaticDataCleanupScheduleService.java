package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Runs the admin data-privacy cleanup jobs automatically on the scheduling node. Every job is a thin wrapper around a
 * {@link DataCleanupService} method and is gated by the matching {@code artemis.cleanup.*-schedule-enabled} flag, which
 * defaults to {@code false} (nothing runs automatically until an admin opts in). The cron expressions live under
 * {@code artemis.scheduling}. The cron still fires on schedule, but the body no-ops while the flag is disabled.
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AutomaticDataCleanupScheduleService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticDataCleanupScheduleService.class);

    private final DataCleanupService dataCleanupService;

    private final DataCleanupProperties dataCleanupProperties;

    public AutomaticDataCleanupScheduleService(DataCleanupService dataCleanupService, DataCleanupProperties dataCleanupProperties) {
        this.dataCleanupService = dataCleanupService;
        this.dataCleanupProperties = dataCleanupProperties;
    }

    /**
     * Archives old courses due for a student-data reset and warns their instructors.
     */
    @Scheduled(cron = "${artemis.scheduling.old-courses-warning-time:0 0 2 * * SUN}")
    public void warnOldCoursesReset() {
        if (!dataCleanupProperties.oldCoursesWarningScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: warning instructors of old courses due for a student-data reset");
        SecurityUtils.runAsSystem(dataCleanupService::warnOldCoursesReset);
    }

    /**
     * Resets the student data of old courses that are past the reset grace period.
     */
    @Scheduled(cron = "${artemis.scheduling.old-courses-reset-time:0 0 3 * * SUN}")
    public void resetOldCourses() {
        if (!dataCleanupProperties.oldCoursesResetScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: resetting the student data of old courses");
        SecurityUtils.runAsSystem(dataCleanupService::resetOldCourses);
    }

    /**
     * Deletes the feedback of non-latest results of courses that ended before the configured cutoff.
     */
    @Scheduled(cron = "${artemis.scheduling.old-feedback-cleanup-time:0 30 4 * * SUN}")
    public void deleteOldFeedback() {
        if (!dataCleanupProperties.oldFeedbackScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: deleting feedback of non-latest results of old courses");
        SecurityUtils.runAsSystem(dataCleanupService::deleteFeedbackOfNonLatestResultsOfOldCourses);
    }

    /**
     * Deletes the submission versions of courses that ended before the configured cutoff.
     */
    @Scheduled(cron = "${artemis.scheduling.old-submission-versions-cleanup-time:0 45 4 * * SUN}")
    public void deleteOldSubmissionVersions() {
        if (!dataCleanupProperties.oldSubmissionVersionsScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: deleting submission versions of old courses");
        SecurityUtils.runAsSystem(dataCleanupService::deleteOldCourseSubmissionVersions);
    }

    /**
     * Warns users who are enrolled in no course and inactive beyond the configured guard period that their account will
     * be deleted after the grace period (phase 1 of the not-enrolled-user cleanup).
     */
    @Scheduled(cron = "${artemis.scheduling.not-enrolled-users-warning-time:0 0 5 1 * *}")
    public void warnNotEnrolledUsers() {
        if (!dataCleanupProperties.notEnrolledUsersWarningScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: warning not-enrolled, inactive users about an upcoming account deletion");
        SecurityUtils.runAsSystem(dataCleanupService::warnNotEnrolledUsers);
    }

    /**
     * Permanently deletes users who were warned, whose grace period has elapsed, who are still not-enrolled and inactive,
     * and whose course-owned data has already been cleaned so no blocking domain references remain.
     */
    @Scheduled(cron = "${artemis.scheduling.not-enrolled-users-cleanup-time:0 0 6 1 * *}")
    public void deleteNotEnrolledUsers() {
        if (!dataCleanupProperties.notEnrolledUsersScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: permanently deleting eligible warned not-enrolled, inactive users");
        SecurityUtils.runAsSystem(dataCleanupService::deleteNotEnrolledUsers);
    }

    /**
     * Deletes the plagiarism cases of courses that ended before the grade-relevant retention cutoff (5 years by default).
     */
    @Scheduled(cron = "${artemis.scheduling.plagiarism-cases-cleanup-time:0 30 3 * * SUN}")
    public void deletePlagiarismCases() {
        if (!dataCleanupProperties.plagiarismCasesScheduleEnabled()) {
            return;
        }
        log.info("Scheduled data-privacy cleanup: deleting plagiarism cases of old courses");
        SecurityUtils.runAsSystem(dataCleanupService::deletePlagiarismCasesOfOldCourses);
    }
}
