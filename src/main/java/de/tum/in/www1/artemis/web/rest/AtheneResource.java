package de.tum.in.www1.artemis.web.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;

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
     * @param requestBody The calculation results containing blocks and clusters
     * @param auth The secret for authorization
     * @return 200 Ok if successful or 401 unauthorized if secret is wrong
     */
    @PostMapping(value = "/{exerciseId}", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Result> saveAtheneResult(@PathVariable Long exerciseId, @RequestBody AtheneDTO requestBody, @RequestHeader("Authorization") String auth) {
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
        atheneService.processResult(requestBody.getClusters(), requestBody.getBlocks(), exerciseId);

        log.debug("REST call for new Athene results for exercise {} finished", exerciseId);

        return ResponseEntity.ok().build();
    }

}
