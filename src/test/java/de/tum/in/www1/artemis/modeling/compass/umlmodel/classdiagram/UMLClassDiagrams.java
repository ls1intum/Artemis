package de.tum.in.www1.artemis.modeling.compass.umlmodel.classdiagram;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

final class UMLClassDiagrams {

    public static final String CLASS_MODEL_1;

    public static final String CLASS_MODEL_2;

    static {
        try {
            CLASS_MODEL_1 = IOUtils.toString(UMLClassDiagrams.class.getResource("classModel1.json"), StandardCharsets.UTF_8);
            CLASS_MODEL_2 = IOUtils.toString(UMLClassDiagrams.class.getResource("classModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLClassDiagrams() {
        // do not instantiate
    }
}
