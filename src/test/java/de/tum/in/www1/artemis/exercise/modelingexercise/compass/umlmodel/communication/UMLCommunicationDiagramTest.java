package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.communication;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLCommunicationDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLCommunicationLink;
import de.tum.in.www1.artemis.service.compass.umlmodel.object.UMLObject;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

class UMLCommunicationDiagramTest extends AbstractUMLDiagramTest {

    @Test
    void similarityCommunicationDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_1), modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2), modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2), 0.8, 100.0);
    }

    @Test
    void similarityCommunicationDiagram_v3_EqualModels() {
        compareSubmissions(modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_1_V3), modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_1_V3), 0.8,
                100.0);
        compareSubmissions(modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2_V3), modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2_V3), 0.8,
                100.0);
    }

    @Test
    void similarityCommunicationDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_1), modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2), 0.0, 22.41);
    }

    @Test
    void similarityCommunicationDiagram_v3_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_1_V3), modelingSubmission(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2_V3), 0.0,
                22.53);
    }

    @Test
    void parseCommunicationDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLCommunicationDiagram.class);
        UMLCommunicationDiagram communicationDiagram = (UMLCommunicationDiagram) diagram;
        assertThat(communicationDiagram.getObjectList()).hasSize(5);
        assertThat(communicationDiagram.getCommunicationLinkList()).hasSize(5);

        assertThat(communicationDiagram.getElementByJSONID("619ddf50-f2a6-4004-9fb3-db64ee10cd6e")).isInstanceOf(UMLObject.class);
        assertThat(communicationDiagram.getElementByJSONID("64040203-6e35-4b42-8ab5-71b544a70fa6")).isInstanceOf(UMLCommunicationLink.class);
    }

    @Test
    void parseCommunicationDiagramModelCorrectly_v3() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLCommunicationDiagrams.COMMUNICATION_MODEL_2_V3).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLCommunicationDiagram.class);
        UMLCommunicationDiagram communicationDiagram = (UMLCommunicationDiagram) diagram;
        assertThat(communicationDiagram.getObjectList()).hasSize(5);
        assertThat(communicationDiagram.getCommunicationLinkList()).hasSize(5);

        assertThat(communicationDiagram.getElementByJSONID("619ddf50-f2a6-4004-9fb3-db64ee10cd6e")).isInstanceOf(UMLObject.class);
        assertThat(communicationDiagram.getElementByJSONID("64040203-6e35-4b42-8ab5-71b544a70fa6")).isInstanceOf(UMLCommunicationLink.class);
    }
}
