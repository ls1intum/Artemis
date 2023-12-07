package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.bpmn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class BPMNDiagrams {

    public static final String COMPONENT_MODEL_1;

    public static final String COMPONENT_MODEL_2;

    public static final String COMPONENT_MODEL_3;

    static {
        try {
            COMPONENT_MODEL_1 = IOUtils.toString(BPMNDiagrams.class.getResource("componentModel1.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_2 = IOUtils.toString(BPMNDiagrams.class.getResource("componentModel2.json"), StandardCharsets.UTF_8);
            COMPONENT_MODEL_3 = IOUtils.toString(BPMNDiagrams.class.getResource("componentModel3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BPMNDiagrams() {
        // do not instantiate
    }
}
