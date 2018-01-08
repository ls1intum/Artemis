package de.tum.in.www1.exerciseapp.web.rest;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by Josias Montag on 14.12.16.
 */

@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class PlantUmlResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantuml PlantUML command(s)
     * @return ResponseEntity PNG stream
     * @throws IOException
     */
    @GetMapping(value = "/plantuml/png")
    public ResponseEntity<byte[]> generatePng(@RequestParam("plantuml") String plantuml) throws IOException {

        // Create PNG output stream
        ByteArrayOutputStream png = new ByteArrayOutputStream();

        // Create input stream reader
        SourceStringReader reader = new SourceStringReader(plantuml);

        // Create PNG
        String dest = reader.generateImage(png);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.IMAGE_PNG);

        return new ResponseEntity(png.toByteArray(), responseHeaders, HttpStatus.OK);
    }
}
