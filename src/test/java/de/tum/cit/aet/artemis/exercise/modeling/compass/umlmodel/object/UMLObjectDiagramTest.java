package de.tum.cit.aet.artemis.exercise.modeling.compass.umlmodel.object;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.modeling.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLDiagram;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object.UMLObject;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object.UMLObjectDiagram;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object.UMLObjectLink;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.UMLModelParser;

class UMLObjectDiagramTest extends AbstractUMLDiagramTest {

    @Test
    void similarityObjectDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_1), modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_2), modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_2), 0.8, 100.0);
    }

    @Test
    void similarityObjectDiagram_v3_EqualModels() {
        compareSubmissions(modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_1_V3), modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_1_V3), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_2), modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_2_V3), 0.8, 100.0);
    }

    @Test
    void similarityObjectDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_1), modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_2), 0.0, 9.99);
    }

    @Test
    void similarityObjectDiagram_v3_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_1_V3), modelingSubmission(UMLObjectDiagrams.OBJECT_MODEL_2_V3), 0.0, 9.99);
    }

    @Test
    void parseObjectDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLObjectDiagrams.OBJECT_MODEL_2).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLObjectDiagram.class);
        UMLObjectDiagram communicationDiagram = (UMLObjectDiagram) diagram;
        assertThat(communicationDiagram.getObjectList()).hasSize(3);
        assertThat(communicationDiagram.getObjectLinkList()).hasSize(2);

        assertThat(communicationDiagram.getElementByJSONID("cf26446e-06ea-4e25-99c4-ded25948e856")).isInstanceOf(UMLObject.class);
        assertThat(communicationDiagram.getElementByJSONID("ea1a2901-eefd-4ffe-a64c-b8e84f977c48")).isInstanceOf(UMLObjectLink.class);
    }

    @Test
    void parseObjectDiagramModelCorrectly_v3() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLObjectDiagrams.OBJECT_MODEL_2_V3).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLObjectDiagram.class);
        UMLObjectDiagram communicationDiagram = (UMLObjectDiagram) diagram;
        assertThat(communicationDiagram.getObjectList()).hasSize(3);
        assertThat(communicationDiagram.getObjectLinkList()).hasSize(2);

        assertThat(communicationDiagram.getElementByJSONID("cf26446e-06ea-4e25-99c4-ded25948e856")).isInstanceOf(UMLObject.class);
        assertThat(communicationDiagram.getElementByJSONID("ea1a2901-eefd-4ffe-a64c-b8e84f977c48")).isInstanceOf(UMLObjectLink.class);
    }
}
