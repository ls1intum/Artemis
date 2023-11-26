package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLComponentDiagrams {

    public static final String COMPONENT_MODEL_1;

    public static final String COMPONENT_MODEL_2;

    public static final String COMPONENT_MODEL_3;

    public static final String COMPONENT_MODEL_1_V3;

    public static final String COMPONENT_MODEL_2_V3;

    public static final String COMPONENT_MODEL_3_V3;

    static {
        try {
            COMPONENT_MODEL_1 = IOUtils.toString(UMLComponentDiagrams.class.getResource("componentModel1.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_2 = IOUtils.toString(UMLComponentDiagrams.class.getResource("componentModel2.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_3 = IOUtils.toString(UMLComponentDiagrams.class.getResource("componentModel3.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_1_V3 = IOUtils.toString(UMLComponentDiagrams.class.getResource("componentModel1v3.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_2_V3 = IOUtils.toString(UMLComponentDiagrams.class.getResource("componentModel2v3.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_3_V3 = IOUtils.toString(UMLComponentDiagrams.class.getResource("componentModel3v3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLComponentDiagrams() {
        // do not instantiate
    }
}
