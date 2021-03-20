package de.tum.in.www1.artemis.web.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.web.rest.dto.ApollonConversionDTO;

/**
 * REST controller for managing ApollonDiagram.
 */
@RestController
@RequestMapping("/api")
public class ApollonConversionResource {

    private final Logger log = LoggerFactory.getLogger(ApollonConversionResource.class);

    private final ApollonConversionService apollonConversionService;

    public ApollonConversionResource(ApollonConversionService apollonConversionService) {
        this.apollonConversionService = apollonConversionService;
    }

    /**
     * Saves automatic textAssessments of Athene
     *
     * @param requestBody The calculation results containing blocks and clusters
     * @return 200 Ok if successful or 401 unauthorized if secret is wrong
     */
    @PostMapping(value = "/pdf", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Result> convertApollonDiagram(@RequestBody ApollonConversionDTO requestBody) {
        log.debug("REST call to inform about new Athene results for exercise: {}");

        // The apollonConversionService will manage the processing and database saving
        apollonConversionService.convertDiagram(requestBody.getDiagram());

        log.debug("REST call for new Athene results for exercise {} finished");

        return ResponseEntity.ok().build();
    }
}
