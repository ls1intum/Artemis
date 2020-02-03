package de.tum.in.www1.artemis.service.compass.controller;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivity;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivityDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivityNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLControlFlow;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;

@ExtendWith(MockitoExtension.class)
class SimilarityDetectorTest {

    @Mock
    UMLClassDiagram classDiagram;

    @Mock
    UMLActivityDiagram activityDiagram;

    @Mock
    ModelIndex modelIndex;

    @Test
    void analyzeSimilarity_ClassDiagram() {
        UMLAttribute attribute1 = new UMLAttribute("attribute1", "String", "attribute1Id");
        UMLAttribute attribute2 = new UMLAttribute("attribute2", "int", "attribute2Id");
        UMLMethod method1 = new UMLMethod("method1(): void", "method1", "void", emptyList(), "method1Id");
        UMLMethod method2 = new UMLMethod("method2(): void", "method2", "void", emptyList(), "method2Id");
        UMLClass class1 = new UMLClass("class1", emptyList(), emptyList(), "class1Id", UMLClass.UMLClassType.INTERFACE);
        UMLClass class2 = new UMLClass("class2", List.of(attribute1, attribute2), List.of(method1, method2), "class2Id", UMLClass.UMLClassType.CLASS);
        UMLRelationship relationship1 = new UMLRelationship(class1, class2, UMLRelationship.UMLRelationshipType.CLASS_BIDIRECTIONAL, "relationship1Id", "", "", "", "");
        UMLRelationship relationship2 = new UMLRelationship(class2, class1, UMLRelationship.UMLRelationshipType.CLASS_REALIZATION, "relationship2Id", "", "", "", "");
        UMLPackage package1 = new UMLPackage("package1", List.of(class1, class2), "package1Id");
        UMLPackage package2 = new UMLPackage("package2", emptyList(), "package2Id");
        classDiagram = new UMLClassDiagram(123456789, List.of(class1, class2), List.of(relationship1, relationship2), List.of(package1, package2));
        List<UMLElement> elements = List.of(class1, class2, relationship1, relationship2, package1, package2, attribute1, attribute2, method1, method2);
        prepareModelIndex(elements);

        SimilarityDetector.analyzeSimilarity(classDiagram, modelIndex);

        verifySimilarityIds(elements);
        verifyContext(List.of(attribute1, attribute2, method1, method2), 2);
        verifyContext(List.of(class1, class2, relationship1, relationship2, package1, package2), -1);
    }

    @Test
    void analyzeSimilarity_ClassDiagram_emptyDiagram() {
        classDiagram = new UMLClassDiagram(123456789, emptyList(), emptyList(), emptyList());

        SimilarityDetector.analyzeSimilarity(classDiagram, modelIndex);
    }

    @Test
    void analyzeSimilarity_ActivityDiagram() {
        UMLActivityNode activityNode1 = new UMLActivityNode("activityNode1", "activityNode1Id", UMLActivityNode.UMLActivityNodeType.ACTIVITY_ACTION_NODE);
        UMLActivityNode activityNode2 = new UMLActivityNode("activityNode2", "activityNode2Id", UMLActivityNode.UMLActivityNodeType.ACTIVITY_DECISION_NODE);
        UMLControlFlow controlFlow1 = new UMLControlFlow(activityNode1, activityNode2, "controlFlow1Id");
        UMLControlFlow controlFlow2 = new UMLControlFlow(activityNode2, activityNode1, "controlFlow2Id");
        UMLActivity activity1 = new UMLActivity("activity1", List.of(activityNode1, activityNode2), "activity1Id");
        UMLActivity activity2 = new UMLActivity("activity2", List.of(activity1), "activity2Id");
        activityDiagram = new UMLActivityDiagram(123456789, List.of(activityNode1, activityNode2), List.of(activity1, activity2), List.of(controlFlow1, controlFlow2));
        List<UMLElement> elements = List.of(activityNode1, activityNode2, controlFlow1, controlFlow2, activity1, activity2);
        prepareModelIndex(elements);

        SimilarityDetector.analyzeSimilarity(activityDiagram, modelIndex);

        verifySimilarityIds(elements);
        verifyContext(elements, -1);
    }

    @Test
    void analyzeSimilarity_ActivityDiagram_emptyDiagram() {
        activityDiagram = new UMLActivityDiagram(123456789, emptyList(), emptyList(), emptyList());

        SimilarityDetector.analyzeSimilarity(activityDiagram, modelIndex);
    }

    private void prepareModelIndex(List<UMLElement> elements) {
        int similarityId = 1;
        for (UMLElement element : elements) {
            when(modelIndex.retrieveSimilarityId(element)).thenReturn(similarityId);
            similarityId++;
        }
    }

    private void verifySimilarityIds(List<UMLElement> elements) {
        int similarityId = 1;
        for (UMLElement element : elements) {
            assertThat(element.getSimilarityID()).isEqualTo(similarityId);
            similarityId++;
        }
    }

    private void verifyContext(List<UMLElement> elements, int contextElementId) {
        Context expectedContext = new Context(contextElementId);
        for (UMLElement element : elements) {
            assertThat(element.getContext()).isEqualTo(expectedContext);
        }
    }
}
