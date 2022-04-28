package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

@Service
public class PlantUmlService {

    public PlantUmlService() {
        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
        System.setProperty("plantuml.allowlist.path", "/Users/pat/projects/Artemis/src/main/resources/puml");
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
     * @return The generated PNG as a byte array
     * @throws IOException if generateImage can't create the PNG
     */
    @Cacheable(value = "plantUmlPng", unless = "#result == null || #result.length == 0")
    public byte[] generatePng(final String plantUml) throws IOException {
        validateInput(plantUml);
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(plantUml);
            reader.outputImage(bos, new FileFormatOption(FileFormat.PNG));
            return bos.toByteArray();
        }
    }

    /**
     * Generate SVG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the SVG
     */
    @Cacheable(value = "plantUmlSvg", unless = "#result == null || #result.isEmpty()")
    public String generateSvg(final String plantUml) throws IOException {
        validateInput(plantUml);
        try (final var bos = new ByteArrayOutputStream()) {
            var st = plantUml.replace("@startuml", "@startuml\n!theme artemisdark2 from /Users/pat/projects/Artemis/src/main/resources/puml");
            System.out.println(st);
            final var reader = new SourceStringReader(st);
            reader.outputImage(bos, new FileFormatOption(FileFormat.SVG));
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private void validateInput(final String plantUml) {
        if (!StringUtils.hasText(plantUml)) {
            throw new IllegalArgumentException("The plantUml input cannot be empty");
        }
        if (plantUml.length() > 10000) {
            throw new IllegalArgumentException("Cannot parse plantUml input longer than 10.000 characters");
        }
    }
}
