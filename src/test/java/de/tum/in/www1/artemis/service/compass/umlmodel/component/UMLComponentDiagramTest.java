package de.tum.in.www1.artemis.service.compass.umlmodel.component;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponentRelationship.UMLComponentRelationshipType.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

class UMLComponentDiagramTest extends AbstractUMLDiagramTest {

    @Test
    void similarityComponentDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_1), modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_2), modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_2), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_3), modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_3), 0.8, 100.0);
    }

    @Test
    void similarityComponentDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_1), modelingSubmission(UMLComponentDiagrams.COMPONENT_MODEL_3), 0.0, 13.48);
    }

    @Test
    void parseComponentDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(UMLComponentDiagrams.COMPONENT_MODEL_3).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLComponentDiagram.class);
        UMLComponentDiagram componentDiagram = (UMLComponentDiagram) diagram;
        // 4 Components A, B, C and D
        assertThat(componentDiagram.getComponentList()).hasSize(4);
        UMLComponent componentA = getComponent(componentDiagram, "A");
        UMLComponent componentB = getComponent(componentDiagram, "B");
        UMLComponent componentC = getComponent(componentDiagram, "C");
        UMLComponent componentD = getComponent(componentDiagram, "D");
        // 5 Interfaces: I1, I2, I3, I4, I5
        assertThat(componentDiagram.getComponentInterfaceList()).hasSize(5);
        UMLComponentInterface interfaceI1 = getInterface(componentDiagram, "I1");
        UMLComponentInterface interfaceI2 = getInterface(componentDiagram, "I2");
        UMLComponentInterface interfaceI3 = getInterface(componentDiagram, "I3");
        UMLComponentInterface interfaceI4 = getInterface(componentDiagram, "I4");
        UMLComponentInterface interfaceI5 = getInterface(componentDiagram, "I5");

        // 8 relationships: 3 ComponentInterfaceProvided, 3 ComponentInterfaceRequired, 2 Dependencies
        assertThat(componentDiagram.getComponentRelationshipList()).hasSize(8);
        UMLComponentRelationship relationship1 = getRelationship(componentDiagram, componentA, componentA);
        UMLComponentRelationship relationship2 = getRelationship(componentDiagram, interfaceI1, interfaceI2);
        UMLComponentRelationship relationship3 = getRelationship(componentDiagram, componentA, interfaceI5);
        UMLComponentRelationship relationship4 = getRelationship(componentDiagram, componentD, interfaceI5);
        UMLComponentRelationship relationship5 = getRelationship(componentDiagram, componentD, componentC);
        UMLComponentRelationship relationship6 = getRelationship(componentDiagram, componentD, componentA);
        UMLComponentRelationship relationship7 = getRelationship(componentDiagram, componentB, interfaceI3);
        UMLComponentRelationship relationship8 = getRelationship(componentDiagram, componentC, interfaceI3);

        assertThat(relationship1.getRelationshipType()).isEqualByComparingTo(COMPONENT_DEPENDENCY);
        assertThat(relationship2.getRelationshipType()).isEqualByComparingTo(COMPONENT_INTERFACE_PROVIDED);
        assertThat(relationship3.getRelationshipType()).isEqualByComparingTo(COMPONENT_INTERFACE_PROVIDED);
        assertThat(relationship4.getRelationshipType()).isEqualByComparingTo(COMPONENT_INTERFACE_REQUIRED);
        assertThat(relationship5.getRelationshipType()).isEqualByComparingTo(COMPONENT_DEPENDENCY);
        assertThat(relationship6.getRelationshipType()).isEqualByComparingTo(COMPONENT_INTERFACE_REQUIRED);
        assertThat(relationship7.getRelationshipType()).isEqualByComparingTo(COMPONENT_INTERFACE_REQUIRED);
        assertThat(relationship8.getRelationshipType()).isEqualByComparingTo(COMPONENT_INTERFACE_PROVIDED);

        // check owner relationships
        assertThat(componentA.getParentElement()).isNull();
        assertThat(componentB.getParentElement()).isEqualTo(componentA);
        assertThat(componentC.getParentElement()).isEqualTo(componentB);
        assertThat(componentD.getParentElement()).isNull();

        assertThat(interfaceI1.getParentElement()).isNull();
        assertThat(interfaceI2.getParentElement()).isEqualTo(componentA);
        assertThat(interfaceI3.getParentElement()).isEqualTo(componentB);
        assertThat(interfaceI4.getParentElement()).isEqualTo(componentC);
        assertThat(interfaceI5.getParentElement()).isNull();
    }
}
