package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.activity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLActivityDiagrams {

    public static final String ACTIVITY_MODEL_1;

    public static final String ACTIVITY_MODEL_2;

    public static final String ACTIVITY_MODEL_3;

    public static final String ACTIVITY_MODEL_1_V3;

    public static final String ACTIVITY_MODEL_2_V3;

    public static final String ACTIVITY_MODEL_3_V3;

    static {
        try {
            ACTIVITY_MODEL_1 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel1.json"), StandardCharsets.UTF_8);
            ACTIVITY_MODEL_2 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel2.json"), StandardCharsets.UTF_8);
            ACTIVITY_MODEL_3 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel3.json"), StandardCharsets.UTF_8);

            ACTIVITY_MODEL_1_V3 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel1v3.json"), StandardCharsets.UTF_8);
            ACTIVITY_MODEL_2_V3 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel2v3.json"), StandardCharsets.UTF_8);
            ACTIVITY_MODEL_3_V3 = IOUtils.toString(UMLActivityDiagrams.class.getResource("activityModel3v3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLActivityDiagrams() {
        // do not instantiate
    }
}
