package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.bpmn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class BPMNDiagrams {

    public static final String BPMN_MODEL_1;

    public static final String BPMN_MODEL_2;

    public static final String BPMN_MODEL_3;

    static {
        try {
            BPMN_MODEL_1 = IOUtils.toString(BPMNDiagrams.class.getResource("bpmnModel1.json"), StandardCharsets.UTF_8);
            BPMN_MODEL_2 = IOUtils.toString(BPMNDiagrams.class.getResource("bpmnModel2.json"), StandardCharsets.UTF_8);
            BPMN_MODEL_3 = IOUtils.toString(BPMNDiagrams.class.getResource("bpmnModel3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BPMNDiagrams() {
        // do not instantiate
    }
}
