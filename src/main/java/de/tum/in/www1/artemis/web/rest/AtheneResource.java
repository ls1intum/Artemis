package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.connectors.AtheneService;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * REST controller for managing Athene results.
 */
@RestController
@RequestMapping(Constants.ATHENE_RESULT_API_PATH)
@Profile("athene")
public class AtheneResource {

    @Value("${artemis.athene.base64-secret}")
    private String API_SECRET;

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
    @Transactional
    public ResponseEntity<Result> saveAtheneResult(@PathVariable Long exerciseId, @RequestBody AtheneDTO requestBody, @RequestHeader("Authorization") String auth) {
        log.debug("REST request to inform about new Athene results for exercise: {}", exerciseId);

        // Check Authorization header
        if (!auth.equals(API_SECRET)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check if job should be running, otherwise reject results
        if (!atheneService.isTaskRunning(exerciseId)) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }

        // Parse requestBody
        Map<Integer, TextCluster> clusters = requestBody.clusters;
        List<TextBlock> textBlocks = atheneService.parseTextBlocks(requestBody.blocks, exerciseId);

        // The atheneService will manage the processing and database saving
        atheneService.processResult(clusters, textBlocks, exerciseId);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

}
