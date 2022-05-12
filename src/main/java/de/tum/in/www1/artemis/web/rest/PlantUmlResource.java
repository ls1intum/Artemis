package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.service.PlantUmlService;

/**
 * Created by Josias Montag on 14.12.16.
 */

@RestController
@RequestMapping(PlantUmlResource.Endpoints.ROOT)
@PreAuthorize("hasRole('USER')")
public class PlantUmlResource {

    private final Logger log = LoggerFactory.getLogger(PlantUmlResource.class);

    private final PlantUmlService plantUmlService;

    public PlantUmlResource(PlantUmlService plantUmlService) {
        this.plantUmlService = plantUmlService;
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantuml PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping(value = Endpoints.GENERATE_PNG)
    public ResponseEntity<byte[]> generatePng(@RequestParam("plantuml") String plantuml, @RequestParam(value = "useDarkTheme", defaultValue = "false") boolean useDarkTheme)
            throws IOException {
        long start = System.currentTimeMillis();
        final var png = plantUmlService.generatePng(plantuml, useDarkTheme);
        final var responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.IMAGE_PNG);
        log.info("PlantUml.generatePng took {}ms", System.currentTimeMillis() - start);
        return new ResponseEntity<>(png, responseHeaders, HttpStatus.OK);
    }

    /**
     * Generate svn diagram for given PlantUML commands
     *
     * @param plantuml PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping(Endpoints.GENERATE_SVG)
    public ResponseEntity<String> generateSvg(@RequestParam("plantuml") String plantuml, @RequestParam(value = "useDarkTheme", defaultValue = "false") boolean useDarkTheme)
            throws IOException {
        long start = System.currentTimeMillis();
        final var svg = plantUmlService.generateSvg(plantuml, useDarkTheme);
        log.info("PlantUml.generateSvg took {}ms", System.currentTimeMillis() - start);
        return new ResponseEntity<>(svg, HttpStatus.OK);
    }

    public static final class Endpoints {

        public static final String ROOT = "/api/plantuml";

        public static final String GENERATE_PNG = "/png";

        public static final String GENERATE_SVG = "/svg";

        private Endpoints() {
        }
    }
}
