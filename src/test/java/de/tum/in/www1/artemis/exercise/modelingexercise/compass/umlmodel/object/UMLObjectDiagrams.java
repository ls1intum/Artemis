package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLObjectDiagrams {

    static final String OBJECT_MODEL_1;

    static final String OBJECT_MODEL_2;

    static final String OBJECT_MODEL_1_V3;

    static final String OBJECT_MODEL_2_V3;

    static {
        try {
            OBJECT_MODEL_1 = IOUtils.toString(UMLObjectDiagrams.class.getResource("objectModel1.json"), StandardCharsets.UTF_8);
            OBJECT_MODEL_2 = IOUtils.toString(UMLObjectDiagrams.class.getResource("objectModel2.json"), StandardCharsets.UTF_8);
            OBJECT_MODEL_1_V3 = IOUtils.toString(UMLObjectDiagrams.class.getResource("objectModel1v3.json"), StandardCharsets.UTF_8);
            OBJECT_MODEL_2_V3 = IOUtils.toString(UMLObjectDiagrams.class.getResource("objectModel2v3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLObjectDiagrams() {
        // do not instantiate
    }
}
