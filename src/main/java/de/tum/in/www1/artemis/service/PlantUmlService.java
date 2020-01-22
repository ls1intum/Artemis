package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sourceforge.plantuml.SourceStringReader;

import org.springframework.stereotype.Service;

@Service
public class PlantUmlService {

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
     * @throws IOException if generateImage can't create the PNG
     */
    public byte[] generatePng(final String plantUml) throws IOException {
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(plantUml);

            reader.generateImage(bos);
            return bos.toByteArray();
        }
    }
}
