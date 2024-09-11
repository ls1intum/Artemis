package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.TimeLogUtil.formatDurationFrom;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.service.PlantUmlService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/plantuml/")
public class PlantUmlResource {

    private static final Logger log = LoggerFactory.getLogger(PlantUmlResource.class);

    private final PlantUmlService plantUmlService;

    public PlantUmlResource(PlantUmlService plantUmlService) {
        this.plantUmlService = plantUmlService;
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantuml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping("png")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> generatePng(@RequestParam("plantuml") String plantuml, @RequestParam(value = "useDarkTheme", defaultValue = "false") boolean useDarkTheme)
            throws IOException {
        long start = System.nanoTime();
        final var png = plantUmlService.generatePng(plantuml, useDarkTheme);
        final var responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.IMAGE_PNG);
        log.debug("PlantUml.generatePng took {}", formatDurationFrom(start));
        return new ResponseEntity<>(png, responseHeaders, HttpStatus.OK);
    }

    /**
     * Generate svn diagram for given PlantUML commands
     *
     * @param plantuml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping("svg")
    @EnforceAtLeastStudent
    public ResponseEntity<String> generateSvg(@RequestParam("plantuml") String plantuml, @RequestParam(value = "useDarkTheme", defaultValue = "false") boolean useDarkTheme)
            throws IOException {
        long start = System.nanoTime();
        final var svg = plantUmlService.generateSvg(plantuml, useDarkTheme);
        log.debug("PlantUml.generateSvg took {}", formatDurationFrom(start));
        return new ResponseEntity<>(svg, HttpStatus.OK);
    }
}
