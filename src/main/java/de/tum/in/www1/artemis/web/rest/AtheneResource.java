package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.ase.athene.protobuf.AtheneResponse;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;

/**
 * REST controller for managing Athene results.
 */
@RestController
@RequestMapping(Constants.ATHENE_RESULT_API_PATH)
@Profile("athene")
public class AtheneResource {

    @Value("${artemis.athene.base64-secret}")
    private String atheneApiSecret;

    private final Logger log = LoggerFactory.getLogger(AtheneResource.class);

    private final AtheneService atheneService;

    public AtheneResource(AtheneService atheneService) {
        this.atheneService = atheneService;
    }

    /**
     * Saves automatic textAssessments of Athene
     *
     * @param exerciseId The exerciseId of the exercise which will be saved
     * @param atheneResponse The calculation results containing blocks and clusters
     * @param auth The secret for authorization
     * @return 200 Ok if successful or 401 unauthorized if secret is wrong
     */
    @PostMapping(value = "/{exerciseId}", consumes = "application/x-protobuf")
    public ResponseEntity<Void> saveAtheneResult(@PathVariable Long exerciseId, @RequestBody AtheneResponse atheneResponse, @RequestHeader("Authorization") String auth) {
        log.debug("REST call to inform about new Athene results for exercise: {}", exerciseId);

        // Check Authorization header
        if (!atheneApiSecret.equals(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check if job should be running, otherwise reject results
        if (!atheneService.isTaskRunning(exerciseId)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }

        // The atheneService will manage the processing and database saving
        atheneService.processResult(atheneResponse.getClustersList(), atheneResponse.getSegmentsList(), exerciseId);

        log.debug("REST call for new Athene results for exercise {} finished", exerciseId);

        return ResponseEntity.ok().build();
    }

}
