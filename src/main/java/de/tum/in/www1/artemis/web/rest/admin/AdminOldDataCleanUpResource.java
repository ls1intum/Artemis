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
 * Provides an endpoint for administrators to clean up old or orphaned data in the database.
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

    /**
     * REST endpoint to trigger the cleanup of old data in the Artemis database based on a user-provided date.
     * This operation will remove old or orphaned data that is older than the specified date.
     * This method is restricted to admin users only.
     *
     * @param cleanupDate the date before which data should be cleaned up. Data older than this date will be deleted.
     * @return an empty HTTP 200 response if the cleanup was successful
     */
    @PostMapping("deleteOldData")
    @EnforceAdmin
    public ResponseEntity<Void> deleteOldData(@RequestParam("cleanupDate") ZonedDateTime cleanupDate) {
        log.debug("REST request to clean up Artemis database for data before {}", cleanupDate);
        this.oldDataCleanupService.cleanupOldData(cleanupDate);
        return ResponseEntity.ok().build();
    }

}
