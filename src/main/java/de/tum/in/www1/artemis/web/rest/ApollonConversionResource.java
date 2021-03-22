package de.tum.in.www1.artemis.web.rest;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * @return 200 Ok if successful or 401 unauthorized if secret is wrong
     */
    @PostMapping("/apollon-convert/pdf")
    public ResponseEntity convertApollonDiagram(@RequestBody ApollonConversionDTO dto) {
        log.debug("REST call to inform about new Athene results for exercise: {}");

        // The apollonConversionService will manage the processing and database saving
        InputStream inputStream = apollonConversionService.convertDiagram(dto.getDiagram());
        // String text = new BufferedReader(
        // new InputStreamReader(inputStream, StandardCharsets.UTF_8))
        // .lines()
        // .collect(Collectors.joining("\n"));
        // System.out.println(text);>
        InputStreamResource inputStreamResource = new InputStreamResource(inputStream);
        log.debug("REST call for new Athene results for exercise {} finished");

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "application/pdf").body(inputStreamResource);
    }
}
