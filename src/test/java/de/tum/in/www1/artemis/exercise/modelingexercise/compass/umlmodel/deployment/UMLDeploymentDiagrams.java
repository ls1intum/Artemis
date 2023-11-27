package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class UMLDeploymentDiagrams {

    public static final String DEPLOYMENT_MODEL_1;

    public static final String DEPLOYMENT_MODEL_2;

    public static final String DEPLOYMENT_MODEL_3;

    public static final String DEPLOYMENT_MODEL_1_V3;

    public static final String DEPLOYMENT_MODEL_2_V3;

    public static final String DEPLOYMENT_MODEL_3_V3;

    static {
        try {
            DEPLOYMENT_MODEL_1 = IOUtils.toString(UMLDeploymentDiagrams.class.getResource("deploymentModel1.json"), StandardCharsets.UTF_8);
            DEPLOYMENT_MODEL_2 = IOUtils.toString(UMLDeploymentDiagrams.class.getResource("deploymentModel2.json"), StandardCharsets.UTF_8);
            DEPLOYMENT_MODEL_3 = IOUtils.toString(UMLDeploymentDiagrams.class.getResource("deploymentModel3.json"), StandardCharsets.UTF_8);
            DEPLOYMENT_MODEL_1_V3 = IOUtils.toString(UMLDeploymentDiagrams.class.getResource("deploymentModel1v3.json"), StandardCharsets.UTF_8);
            DEPLOYMENT_MODEL_2_V3 = IOUtils.toString(UMLDeploymentDiagrams.class.getResource("deploymentModel2v3.json"), StandardCharsets.UTF_8);
            DEPLOYMENT_MODEL_3_V3 = IOUtils.toString(UMLDeploymentDiagrams.class.getResource("deploymentModel3v3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLDeploymentDiagrams() {
        // do not instantiate
    }
}
