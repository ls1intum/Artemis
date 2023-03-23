package de.tum.in.www1.artemis.modeling.compass.umlmodel.activity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLActivityDiagrams {

    public static final String ACTIVITY_MODEL_1;

    public static final String ACTIVITY_MODEL_2;

    static {
        try {
            ACTIVITY_MODEL_1 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel1.json"), StandardCharsets.UTF_8);
            ACTIVITY_MODEL_2 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLActivityDiagrams() {
        // do not instantiate
    }
}
