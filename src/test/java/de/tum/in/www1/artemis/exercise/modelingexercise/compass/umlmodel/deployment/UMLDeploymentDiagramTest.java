package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.deployment;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponent;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.UMLArtifact;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.UMLDeploymentDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.UMLNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

class UMLDeploymentDiagramTest extends AbstractUMLDiagramTest {

    @Test
    void similarityDeploymentDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3), 0.8, 100.0);
    }

    @Test
    void similarityDeploymentDiagram_v3_EqualModels() {
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1_V3), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2_V3), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2_V3), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3_V3), 0.8, 100.0);
    }

    @Test
    void similarityDeploymentDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2), 0.0, 35.65);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3), 0.0, 10.70);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3), 0.0, 11.50);
    }

    @Test
    void similarityDeploymentDiagram_v3_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1_V3), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2), 0.0, 35.65);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_1_V3), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3_V3), 0.0, 10.70);
        compareSubmissions(modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_2), modelingSubmission(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3_V3), 0.0, 11.50);
    }

    @Test
    void parseDeploymentDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLDeploymentDiagram.class);
        UMLDeploymentDiagram deploymentDiagram = (UMLDeploymentDiagram) diagram;
        assertThat(deploymentDiagram.getComponentList()).hasSize(3);
        assertThat(deploymentDiagram.getComponentInterfaceList()).hasSize(1);
        assertThat(deploymentDiagram.getArtifactList()).hasSize(3);
        assertThat(deploymentDiagram.getNodeList()).hasSize(2);
        assertThat(deploymentDiagram.getComponentRelationshipList()).hasSize(7);

        assertThat(deploymentDiagram.getElementByJSONID("d821e11d-6cf1-497b-8462-6533047cb0e8")).isInstanceOf(UMLNode.class);
        assertThat(deploymentDiagram.getElementByJSONID("035638ac-9bb1-4bfb-a2c1-028310ae4c3e")).isInstanceOf(UMLComponent.class);
        assertThat(deploymentDiagram.getElementByJSONID("800db443-242f-4c40-8106-6b2a5fa99d2f")).isInstanceOf(UMLArtifact.class);
    }

    @Test
    void parseDeploymentDiagramModelCorrectly_v3() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLDeploymentDiagrams.DEPLOYMENT_MODEL_3_V3).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLDeploymentDiagram.class);
        UMLDeploymentDiagram deploymentDiagram = (UMLDeploymentDiagram) diagram;
        assertThat(deploymentDiagram.getComponentList()).hasSize(3);
        assertThat(deploymentDiagram.getComponentInterfaceList()).hasSize(1);
        assertThat(deploymentDiagram.getArtifactList()).hasSize(3);
        assertThat(deploymentDiagram.getNodeList()).hasSize(2);
        assertThat(deploymentDiagram.getComponentRelationshipList()).hasSize(7);

        assertThat(deploymentDiagram.getElementByJSONID("d821e11d-6cf1-497b-8462-6533047cb0e8")).isInstanceOf(UMLNode.class);
        assertThat(deploymentDiagram.getElementByJSONID("035638ac-9bb1-4bfb-a2c1-028310ae4c3e")).isInstanceOf(UMLComponent.class);
        assertThat(deploymentDiagram.getElementByJSONID("800db443-242f-4c40-8106-6b2a5fa99d2f")).isInstanceOf(UMLArtifact.class);
    }
}
