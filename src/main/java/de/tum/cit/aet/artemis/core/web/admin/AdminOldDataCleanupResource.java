package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.cleanup.DataCleanupService;

/**
 * REST controller for managing old data cleanup operations in Artemis.
 * Provides endpoints for administrators to clean up old or orphaned data in the database.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminOldDataCleanupResource {

    private static final Logger log = LoggerFactory.getLogger(AdminOldDataCleanupResource.class);

    private final DataCleanupService dataCleanupService;

    public AdminOldDataCleanupResource(DataCleanupService dataCleanupService) {
        this.dataCleanupService = dataCleanupService;
    }

    /**
     * Deletes orphaned data in the Artemis database.
     *
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @PostMapping("delete-orphans")
    @EnforceAdmin
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOrphans() {
        log.debug("REST request to delete orphaned data in Artemis database");
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteOrphans();
        return ResponseEntity.ok().body(result);
    }

    /**
     * Deletes plagiarism comparisons within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @PostMapping("delete-plagiarism-comparisons")
    @EnforceAdmin
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deletePlagiarismComparisons(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete plagiarism comparisons between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deletePlagiarismComparisons(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * Deletes non-rated results within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @PostMapping("delete-non-rated-results")
    @EnforceAdmin
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteNonRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete non-rated results between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteNonRatedResults(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * Deletes old rated results within the specified date range.
     *
     * @param deleteFrom the start date of the deletion range
     * @param deleteTo   the end date of the deletion range
     * @return a {@link ResponseEntity} containing the result of the cleanup operation
     */
    @PostMapping("delete-old-rated-results")
    @EnforceAdmin
    public ResponseEntity<CleanupServiceExecutionRecordDTO> deleteOldRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom,
            @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete old rated results between {} and {}", deleteFrom, deleteTo);
        CleanupServiceExecutionRecordDTO result = dataCleanupService.deleteRatedResults(deleteFrom, deleteTo);
        return ResponseEntity.ok().body(result);
    }

    /**
     * Retrieves the last execution records of the data cleanup operations.
     *
     * @return a {@link ResponseEntity} containing a list of execution records
     */
    @GetMapping("get-last-executions")
    @EnforceAdmin
    public ResponseEntity<List<CleanupServiceExecutionRecordDTO>> getLastExecutions() {
        List<CleanupServiceExecutionRecordDTO> result = dataCleanupService.getLastExecutions();
        return ResponseEntity.ok().body(result);
    }
}
