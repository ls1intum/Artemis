package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.admin.config.LegacyAdminRestPaths;
import de.tum.cit.aet.artemis.admin.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.admin.dto.NonLatestNonRatedResultsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.NonLatestRatedResultsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.NotEnrolledUsersCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OldCoursesCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OldFeedbackCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OrphanCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.PlagiarismCasesCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.PlagiarismComparisonCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.SubmissionVersionsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.service.DataCleanupService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;

/**
 * REST controller for managing old data cleanup operations in Artemis.
 * Provides endpoints for administrators to clean up old or orphaned data in the database.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("data-privacy/data-cleanup")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping({ "api/admin/cleanup/", LegacyAdminRestPaths.CORE_ADMIN_CLEANUP_PREFIX })
@EnforceAdmin
public class AdminCleanupResource {

    private static final Logger log = LoggerFactory.getLogger(AdminCleanupResource.class);

    private final DataCleanupService dataCleanupService;

    public AdminCleanupResource(DataCleanupService dataCleanupService) {
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * DELETE admin/cleanup/orphans
     * Deletes orphaned data in the Artemis database.
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("orphans")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOrphans() {
        log.info("REST request to delete orphaned data in Artemis database");
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteOrphans();
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET admin/cleanup/orphans/count
     * Counts the number of orphaned data entries that would be deleted.
     *
     * @return a {@link ResponseEntity} containing the count of orphaned entries
     */
    @GetMapping("orphans/count")
    public ResponseEntity<OrphanCleanupCountDTO> countOrphans() {
        log.info("REST request to count orphaned data in Artemis database");
        OrphanCleanupCountDTO result = dataCleanupService.countOrphans();
        return ResponseEntity.ok().body(result);
    }

    /**
     * DELETE admin/cleanup/plagiarism-comparisons
     * Deletes plagiarism comparisons within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("plagiarism-comparisons")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deletePlagiarismComparisons(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to delete plagiarism comparisons between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deletePlagiarismComparisons(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET admin/cleanup/plagiarism-comparisons/count
     * Counts the number of plagiarism comparisons and related entries that would be deleted within the specified date range.
     *
     * @param deleteFrom the start date of the counting range
     * @param deleteTo   the end date of the counting range
     * @return a {@link ResponseEntity} containing the count of affected entries
     */
    @GetMapping("plagiarism-comparisons/count")
    public ResponseEntity<PlagiarismComparisonCleanupCountDTO> countPlagiarismComparisons(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to count plagiarism comparisons between {} and {}", deleteFrom, deleteTo);
        PlagiarismComparisonCleanupCountDTO result = dataCleanupService.countPlagiarismComparisons(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * DELETE admin/cleanup/non-rated-results
     * Deletes non-rated results within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("non-rated-results")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteNonRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to delete non-rated results between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteNonLatestNonRatedResultsFeedback(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET admin/cleanup/non-rated-results/count
     * Counts the number of non-rated results and related entries that would be deleted within the specified date range.
     *
     * @param deleteFrom the start date of the counting range
     * @param deleteTo   the end date of the counting range
     * @return a {@link ResponseEntity} containing the count of affected entries
     */
    @GetMapping("non-rated-results/count")
    public ResponseEntity<NonLatestNonRatedResultsCleanupCountDTO> countNonRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to count non-rated results between {} and {}", deleteFrom, deleteTo);
        NonLatestNonRatedResultsCleanupCountDTO result = dataCleanupService.countNonLatestNonRatedResults(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * DELETE admin/cleanup/old-rated-results
     * Deletes old rated results within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("old-rated-results")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOldRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to delete old rated results between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteNonLatestRatedResultsFeedback(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET admin/cleanup/old-rated-results/count
     * Counts the number of old rated results and related entries that would be deleted within the specified date range.
     *
     * @param deleteFrom the start date of the counting range
     * @param deleteTo   the end date of the counting range
     * @return a {@link ResponseEntity} containing the count of affected entries
     */
    @GetMapping("old-rated-results/count")
    public ResponseEntity<NonLatestRatedResultsCleanupCountDTO> countOldRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to count old rated results between {} and {}", deleteFrom, deleteTo);
        NonLatestRatedResultsCleanupCountDTO result = dataCleanupService.countNonLatestRatedResults(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * DELETE admin/cleanup/old-submission-versions
     * Deletes old submission versions within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("old-submission-versions")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOldSubmissionVersions(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to delete old submission versions between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteSubmissionVersions(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET admin/cleanup/old-submission-versions/count
     * Counts the number of submission versions entries that would be deleted within the specified date range.
     *
     * @param deleteFrom the start date of the counting range
     * @param deleteTo   the end date of the counting range
     * @return a {@link ResponseEntity} containing the count of affected entries
     */
    @GetMapping("old-submission-versions/count")
    public ResponseEntity<SubmissionVersionsCleanupCountDTO> countOldSubmissionVersions(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.info("REST request to count old submission versions between {} and {}", deleteFrom, deleteTo);
        SubmissionVersionsCleanupCountDTO result = dataCleanupService.countSubmissionVersions(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * POST admin/cleanup/old-courses/warn
     * Archives old courses that are due for a student-data reset and warns their instructors (with a backup download link).
     *
     * @return a {@link ResponseEntity} containing the result of the operation
     */
    @PostMapping("old-courses/warn")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> warnOldCoursesReset() {
        log.info("REST request to warn instructors and archive old courses due for a student-data reset");
        return ResponseEntity.ok().body(dataCleanupService.warnOldCoursesReset());
    }

    /**
     * GET admin/cleanup/old-courses/warn/count
     * Counts the old courses due for a student-data reset warning.
     *
     * @return a {@link ResponseEntity} containing the affected courses
     */
    @GetMapping("old-courses/warn/count")
    public ResponseEntity<OldCoursesCleanupCountDTO> countOldCoursesResetWarning() {
        log.info("REST request to count old courses due for a student-data reset warning");
        return ResponseEntity.ok().body(dataCleanupService.countOldCoursesResetWarning());
    }

    /**
     * DELETE admin/cleanup/old-courses/reset
     * Resets the student data of old courses that are past the reset grace period, keeping the course material.
     *
     * @return a {@link ResponseEntity} containing the result of the operation
     */
    @DeleteMapping("old-courses/reset")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> resetOldCourses() {
        log.info("REST request to reset the student data of old courses");
        return ResponseEntity.ok().body(dataCleanupService.resetOldCourses());
    }

    /**
     * GET admin/cleanup/old-courses/reset/count
     * Counts the old courses due for a student-data reset.
     *
     * @return a {@link ResponseEntity} containing the affected courses
     */
    @GetMapping("old-courses/reset/count")
    public ResponseEntity<OldCoursesCleanupCountDTO> countOldCoursesReset() {
        log.info("REST request to count old courses due for a student-data reset");
        return ResponseEntity.ok().body(dataCleanupService.countOldCoursesReset());
    }

    /**
     * DELETE admin/cleanup/old-feedback
     * Deletes the feedback of non-latest results for courses that ended before the configured cutoff (results are kept).
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("old-feedback")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOldFeedback() {
        log.info("REST request to delete feedback of non-latest results of old courses");
        return ResponseEntity.ok().body(dataCleanupService.deleteFeedbackOfNonLatestResultsOfOldCourses());
    }

    /**
     * GET admin/cleanup/old-feedback/count
     * Counts the feedback of non-latest results that would be deleted for courses that ended before the configured cutoff.
     *
     * @return a {@link ResponseEntity} containing the count of affected entries
     */
    @GetMapping("old-feedback/count")
    public ResponseEntity<OldFeedbackCleanupCountDTO> countOldFeedback() {
        log.info("REST request to count feedback of non-latest results of old courses");
        return ResponseEntity.ok().body(dataCleanupService.countFeedbackOfNonLatestResultsOfOldCourses());
    }

    /**
     * DELETE admin/cleanup/old-course-submission-versions
     * Deletes the submission versions of courses that ended before the configured cutoff.
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("old-course-submission-versions")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOldCourseSubmissionVersions() {
        log.info("REST request to delete submission versions of old courses");
        return ResponseEntity.ok().body(dataCleanupService.deleteOldCourseSubmissionVersions());
    }

    /**
     * GET admin/cleanup/old-course-submission-versions/count
     * Counts the submission versions of courses that ended before the configured cutoff.
     *
     * @return a {@link ResponseEntity} containing the count of affected entries
     */
    @GetMapping("old-course-submission-versions/count")
    public ResponseEntity<SubmissionVersionsCleanupCountDTO> countOldCourseSubmissionVersions() {
        log.info("REST request to count submission versions of old courses");
        return ResponseEntity.ok().body(dataCleanupService.countOldCourseSubmissionVersions());
    }

    /**
     * POST admin/cleanup/not-enrolled-users/warn
     * Warns not-enrolled, inactive users (by email) that their account will be deleted after the grace period.
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @PostMapping("not-enrolled-users/warn")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> warnNotEnrolledUsers() {
        log.info("REST request to warn not-enrolled, inactive users about an upcoming account deletion");
        return ResponseEntity.ok().body(dataCleanupService.warnNotEnrolledUsers());
    }

    /**
     * GET admin/cleanup/not-enrolled-users/warn/count
     * Counts the not-enrolled, inactive users that would be warned about an upcoming account deletion.
     *
     * @return a {@link ResponseEntity} containing the count of affected users
     */
    @GetMapping("not-enrolled-users/warn/count")
    public ResponseEntity<NotEnrolledUsersCleanupCountDTO> countNotEnrolledUsersWarning() {
        log.info("REST request to count not-enrolled, inactive users to warn about an upcoming account deletion");
        return ResponseEntity.ok().body(dataCleanupService.countNotEnrolledUsersWarning());
    }

    /**
     * DELETE admin/cleanup/not-enrolled-users
     * Permanently deletes warned users whose grace period has elapsed, who are still not-enrolled and inactive, and who have no blocking domain references.
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("not-enrolled-users")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteNotEnrolledUsers() {
        log.info("REST request to permanently delete eligible warned not-enrolled, inactive users");
        return ResponseEntity.ok().body(dataCleanupService.deleteNotEnrolledUsers());
    }

    /**
     * GET admin/cleanup/not-enrolled-users/count
     * Counts the warned users that would be permanently deleted and those blocked by remaining domain references.
     *
     * @return a {@link ResponseEntity} containing the count of affected users
     */
    @GetMapping("not-enrolled-users/count")
    public ResponseEntity<NotEnrolledUsersCleanupCountDTO> countNotEnrolledUsers() {
        log.info("REST request to count warned not-enrolled, inactive users");
        return ResponseEntity.ok().body(dataCleanupService.countNotEnrolledUsers());
    }

    /**
     * DELETE admin/cleanup/plagiarism-cases
     * Deletes the plagiarism cases of courses that ended before the grade-relevant retention cutoff (5 years by default).
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @DeleteMapping("plagiarism-cases")
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deletePlagiarismCases() {
        log.info("REST request to delete plagiarism cases of old courses");
        return ResponseEntity.ok().body(dataCleanupService.deletePlagiarismCasesOfOldCourses());
    }

    /**
     * GET admin/cleanup/plagiarism-cases/count
     * Counts the plagiarism cases of courses that ended before the grade-relevant retention cutoff.
     *
     * @return a {@link ResponseEntity} containing the count of affected plagiarism cases
     */
    @GetMapping("plagiarism-cases/count")
    public ResponseEntity<PlagiarismCasesCleanupCountDTO> countPlagiarismCases() {
        log.info("REST request to count plagiarism cases of old courses");
        return ResponseEntity.ok().body(dataCleanupService.countPlagiarismCasesOfOldCourses());
    }

    /**
     * GET admin/cleanup/last-executions
     * Retrieves the last execution records of the data cleanup operations.
     *
     * @return a {@link ResponseEntity} containing a list of execution records
     */
    @GetMapping("last-executions")
    public ResponseEntity<List<CleanupServiceExecutionRecordDTO>> getLastExecutions() {
        List<CleanupServiceExecutionRecordDTO> result = dataCleanupService.getLastExecutions();
        return ResponseEntity.ok().body(result);
    }
}
