package de.tum.in.www1.artemis.modeling.compass.umlmodel.communication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLCommunicationDiagrams {

    public static final String COMMUNICATION_MODEL_1;

    public static final String COMMUNICATION_MODEL_2;

    static {
        try {
            COMMUNICATION_MODEL_1 = IOUtils.toString(UMLCommunicationDiagrams.class.getResource("communicationModel1.json"), StandardCharsets.UTF_8);
            COMMUNICATION_MODEL_2 = IOUtils.toString(UMLCommunicationDiagrams.class.getResource("communicationModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLCommunicationDiagrams() {
        // do not instantiate
    }
}
