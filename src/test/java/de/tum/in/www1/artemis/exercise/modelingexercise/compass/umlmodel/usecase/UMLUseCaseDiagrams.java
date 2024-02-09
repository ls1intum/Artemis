package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.usecase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLUseCaseDiagrams {

    public static final String USE_CASE_MODEL_1;

    public static final String USE_CASE_MODEL_2;

    public static final String USE_CASE_MODEL_1_V3;

    public static final String USE_CASE_MODEL_2_V3;

    static {
        try {
            USE_CASE_MODEL_1 = IOUtils.toString(UMLUseCaseDiagrams.class.getResource("useCaseModel1.json"), StandardCharsets.UTF_8);
            USE_CASE_MODEL_2 = IOUtils.toString(UMLUseCaseDiagrams.class.getResource("useCaseModel2.json"), StandardCharsets.UTF_8);
            USE_CASE_MODEL_1_V3 = IOUtils.toString(UMLUseCaseDiagrams.class.getResource("useCaseModel1v3.json"), StandardCharsets.UTF_8);
            USE_CASE_MODEL_2_V3 = IOUtils.toString(UMLUseCaseDiagrams.class.getResource("useCaseModel2v3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLUseCaseDiagrams() {
        // do not instantiate
    }
}
