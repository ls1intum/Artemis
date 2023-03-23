package de.tum.in.www1.artemis.modeling.compass.umlmodel.object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLObjectDiagrams {

    static final String OBJECT_MODEL_1;

    static final String OBJECT_MODEL_2;

    static {
        try {
            OBJECT_MODEL_1 = IOUtils.toString(UMLObjectDiagrams.class.getResource("objectModel1.json"), StandardCharsets.UTF_8);
            OBJECT_MODEL_2 = IOUtils.toString(UMLObjectDiagrams.class.getResource("objectModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLObjectDiagrams() {
        // do not instantiate
    }
}
