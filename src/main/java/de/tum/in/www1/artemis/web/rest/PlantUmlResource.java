package de.tum.in.www1.artemis.web.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.service.PlantUmlService;

/**
 * Created by Josias Montag on 14.12.16.
 */

@RestController
@RequestMapping(PlantUmlResource.Endpoints.ROOT)
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class PlantUmlResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final PlantUmlService plantUmlService;

    public PlantUmlResource(PlantUmlService plantUmlService) {
        this.plantUmlService = plantUmlService;
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantuml PlantUML command(s)
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping(value = Endpoints.GENERATE_PNG)
    public ResponseEntity<byte[]> generatePng(@RequestParam("plantuml") String plantuml) throws IOException {
        final var png = plantUmlService.generatePng(plantuml);
        final var responseHeaders = new HttpHeaders();

        responseHeaders.setContentType(MediaType.IMAGE_PNG);

        return new ResponseEntity<>(png, responseHeaders, HttpStatus.OK);
    }

    /**
     * Generate svn diagram for given PlantUML commands
     *
     * @param plantuml PlantUML command(s)
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping(value = "/plantuml/svg")
    public ResponseEntity<String> generateSvg(@RequestParam("plantuml") String plantuml) throws IOException {

        // Create SVG output stream
        ByteArrayOutputStream svgOutputStream = new ByteArrayOutputStream();

        // Create input stream reader
        SourceStringReader reader = new SourceStringReader(plantuml);

        // Create SVN
        reader.generateImage(svgOutputStream, new FileFormatOption(FileFormat.SVG));
        svgOutputStream.close();

        HttpHeaders responseHeaders = new HttpHeaders();
        // The XML is stored into svg
        final String svg = new String(svgOutputStream.toByteArray(), StandardCharsets.UTF_8);

        return new ResponseEntity<>(svg, responseHeaders, HttpStatus.OK);
    }

    public static final class Endpoints {

        public static final String ROOT = "/api/plantuml";

        public static final String GENERATE_PNG = "/png";

        public static final String GENERATE_SVG = "/svg";
    }
}
