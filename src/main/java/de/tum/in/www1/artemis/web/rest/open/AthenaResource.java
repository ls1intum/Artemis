package de.tum.in.www1.artemis.web.rest.open;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.ase.athene.protobuf.AtheneResponse;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaService;

/**
 * REST controller for managing Athena results.
 */
@RestController
@RequestMapping("api/public/")
@Profile("athena")
public class AthenaResource {

    @Value("${artemis.athena.base64-secret}")
    private String athenaApiSecret;

    private final Logger log = LoggerFactory.getLogger(AthenaResource.class);

    private final AthenaService athenaService;

    public AthenaResource(AthenaService athenaService) {
        this.athenaService = athenaService;
    }

    /**
     * POST athena-result/:exerciseId -- Saves automatic textAssessments of Athena
     *
     * @param exerciseId     The exerciseId of the exercise which will be saved
     * @param athenaResponse The calculation results containing blocks and clusters
     * @param auth           The secret for authorization
     * @return 200 Ok if successful or 401 unauthorized if secret is wrong
     */
    @PostMapping(value = "athena-result/{exerciseId}", consumes = "application/x-protobuf")
    @EnforceNothing
    public ResponseEntity<Void> saveAthenaResult(@PathVariable Long exerciseId, @RequestBody AtheneResponse athenaResponse, @RequestHeader("Authorization") String auth) {
        log.debug("REST call to inform about new Athena results for exercise: {}", exerciseId);

        // Check Authorization header
        if (!athenaApiSecret.equals(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check if job should be running, otherwise reject results
        if (!athenaService.isTaskRunning(exerciseId)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }

        // The athenaService will manage the processing and database saving
        // athenaService.processResult(athenaResponse.getClustersList(), athenaResponse.getSegmentsList(), exerciseId);

        log.debug("REST call for new Athena results for exercise {} finished", exerciseId);

        return ResponseEntity.ok().build();
    }
}
