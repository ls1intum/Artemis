package de.tum.cit.aet.artemis.web.rest;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.cit.aet.artemis.service.connectors.apollon.dto.ApollonModelDTO;

/**
 * REST controller for managing ApollonDiagram.
 */
@Profile("apollon")
@RestController
@RequestMapping("api/")
public class ApollonConversionResource {

    private static final Logger log = LoggerFactory.getLogger(ApollonConversionResource.class);

    private final ApollonConversionService apollonConversionService;

    public ApollonConversionResource(ApollonConversionService apollonConversionService) {
        this.apollonConversionService = apollonConversionService;
    }

    /**
     * Converts given model to pdf
     *
     * @param request the model for conversion
     * @return input stream for conversion
     */
    @PostMapping("apollon/convert-to-pdf")
    @EnforceAtLeastStudent
    public ResponseEntity<InputStreamResource> convertApollonModel(@RequestBody ApollonModelDTO request) {
        log.debug("REST call to convert apollon model to pdf");

        // The apollonConversionService will manage the processing and database saving
        InputStream inputStream = apollonConversionService.convertModel(request.model());

        InputStreamResource inputStreamResource = new InputStreamResource(inputStream);
        log.debug("REST call for apollon model conversion to pdf finished");

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(inputStreamResource);

    }
}
