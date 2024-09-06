package de.tum.in.www1.artemis.web.rest.admin;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.cleanup.OldDataCleanupService;

/**
 * REST controller for managing old data cleanup operations in Artemis.
 * Provides endpoints for administrators to clean up old or orphaned data in the database.
 */
@RestController
@RequestMapping("api/admin/")
@Profile(PROFILE_CORE)
public class AdminOldDataCleanUpResource {

    private static final Logger log = LoggerFactory.getLogger(AdminOldDataCleanUpResource.class);

    private final OldDataCleanupService oldDataCleanupService;

    public AdminOldDataCleanUpResource(OldDataCleanupService oldDataCleanupService) {
        this.oldDataCleanupService = oldDataCleanupService;
    }

    @PostMapping("delete-orphans")
    @EnforceAdmin
    public ResponseEntity<Void> deleteOrphans() {
        log.debug("REST request to delete orphaned data in Artemis database");
        oldDataCleanupService.deleteOrphans();
        return ResponseEntity.ok().build();
    }

    @PostMapping("delete-plagiarism-comparisons")
    @EnforceAdmin
    public ResponseEntity<Void> deletePlagiarismComparisons(@RequestParam("deleteFrom") ZonedDateTime deleteFrom, @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete plagiarism comparisons between {} and {}", deleteFrom, deleteTo);
        oldDataCleanupService.deletePlagiarismComparisons(deleteFrom, deleteTo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("delete-non-rated-results")
    @EnforceAdmin
    public ResponseEntity<Void> deleteNonRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom, @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete non-rated results between {} and {}", deleteFrom, deleteTo);
        oldDataCleanupService.deleteNonRatedResults(deleteFrom, deleteTo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("delete-old-rated-results")
    @EnforceAdmin
    public ResponseEntity<Void> deleteOldRatedResults(@RequestParam("deleteFrom") ZonedDateTime deleteFrom, @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete old rated results between {} and {}", deleteFrom, deleteTo);
        oldDataCleanupService.deleteOldRatedResults(deleteFrom, deleteTo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("delete-old-submission-versions")
    @EnforceAdmin
    public ResponseEntity<Void> deleteOldSubmissionVersions(@RequestParam("deleteFrom") ZonedDateTime deleteFrom, @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete old submission versions between {} and {}", deleteFrom, deleteTo);
        oldDataCleanupService.deleteSubmissionVersions(deleteFrom, deleteTo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("delete-old-feedback")
    @EnforceAdmin
    public ResponseEntity<Void> deleteOldFeedback(@RequestParam("deleteFrom") ZonedDateTime deleteFrom, @RequestParam("deleteTo") ZonedDateTime deleteTo) {
        log.debug("REST request to delete old feedback between {} and {}", deleteFrom, deleteTo);
        oldDataCleanupService.deleteOldFeedback(deleteFrom, deleteTo);
        return ResponseEntity.ok().build();
    }
}
