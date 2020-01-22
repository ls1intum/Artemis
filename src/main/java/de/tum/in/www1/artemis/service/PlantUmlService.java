package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import org.springframework.stereotype.Service;

@Service
public class PlantUmlService {

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
     * @return The generated PNG as a byte array
     * @throws IOException if generateImage can't create the PNG
     */
    public byte[] generatePng(final String plantUml) throws IOException {
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(plantUml);

            reader.generateImage(bos);
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
    public String generateSvg(final String plantUml) throws IOException {
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(plantUml);

            reader.generateImage(bos, new FileFormatOption(FileFormat.SVG));

            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
