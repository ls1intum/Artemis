package de.tum.in.www1.artemis.service.compass.controller;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode.UMLActivityNodeType.*;
import static de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship.UMLRelationshipType.*;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivity;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLControlFlow;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.in.www1.artemis.util.FileUtils;

class UMLModelParserTest {

    @Test
    void buildModelFromJSON_classDiagram() throws Exception {
        JsonObject classDiagramJson = loadFileFromResources("test-data/model-submission/example-class-diagram.json");
        List<UMLElement> expectedElements = createExampleClassDiagramElements();

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(classDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).containsExactlyInAnyOrderElementsOf(expectedElements);
    }

    @Test
    void buildModelFromJSON_classDiagram_empty() throws Exception {
        JsonObject classDiagramJson = loadFileFromResources("test-data/model-submission/empty-class-diagram.json");

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(classDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    @Test
    void buildModelFromJSON_classDiagram_packageRelationship() throws Exception {
        JsonObject classDiagramJson = loadFileFromResources("test-data/model-submission/example-class-diagram-package-relationship.json");
        UMLPackage umlPackage = new UMLPackage("Package", emptyList(), "94d565ec-c92c-422c-b3bd-347974936348");
        UMLClass umlClass = new UMLClass("Class", emptyList(), emptyList(), "ac9c281b-5b2c-4b3d-87c0-db798bd1f513", UMLClassType.CLASS);

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(classDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).containsExactlyInAnyOrder(umlClass, umlPackage);
    }

    @Test
    void buildModelFromJSON_activityDiagram() throws Exception {
        JsonObject activityDiagramJson = loadFileFromResources("test-data/model-submission/example-activity-diagram.json");
        List<UMLElement> expectedElements = createExampleActivityDiagramElements();

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(activityDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).containsExactlyInAnyOrderElementsOf(expectedElements);
    }

    @Test
    void buildModelFromJSON_activityDiagram_empty() throws Exception {
        JsonObject activityDiagramJson = loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(activityDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    @Test
    void buildModelFromJSON_objectDiagram_empty() throws Exception {
        JsonObject objectDiagramJson = loadFileFromResources("test-data/model-submission/empty-object-diagram.json");

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(objectDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    @Test
    void buildModelFromJSON_useCaseDiagram_empty() throws Exception {
        JsonObject useCaseDiagramJson = loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(useCaseDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    @Test
    void buildModelFromJSON_communicationDiagram_empty() throws Exception {
        JsonObject communicationDiagramJson = loadFileFromResources("test-data/model-submission/empty-communication-diagram.json");
        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(communicationDiagramJson, 123456789);
        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    @Test
    void buildModelFromJSON_deploymentDiagram_empty() throws Exception {
        JsonObject deploymentDiagramJson = loadFileFromResources("test-data/model-submission/empty-deployment-diagram.json");

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(deploymentDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    @Test
    void buildModelFromJSON_componentDiagram_empty() throws Exception {
        JsonObject componentDiagramJson = loadFileFromResources("test-data/model-submission/empty-component-diagram.json");

        UMLDiagram umlDiagram = UMLModelParser.buildModelFromJSON(componentDiagramJson, 123456789);

        assertThat(umlDiagram.getModelSubmissionId()).isEqualTo(123456789);
        assertThat(umlDiagram.getAllModelElements()).isEmpty();
    }

    private JsonObject loadFileFromResources(String path) throws Exception {
        return parseString(FileUtils.loadFileFromResources(path)).getAsJsonObject();
    }

    private List<UMLElement> createExampleClassDiagramElements() {
        List<UMLElement> elements = new ArrayList<>();

        UMLMethod interfaceMethod = new UMLMethod("+ method()", "+method", "", emptyList(), "f9f17062-657c-481e-a605-4bde62335383");
        UMLClass interfaceClass = new UMLClass("Interface", emptyList(), List.of(interfaceMethod), "65cb162e-5b4a-4f9c-b5fe-da622019e627", UMLClassType.INTERFACE);
        elements.add(interfaceMethod);
        elements.add(interfaceClass);

        UMLAttribute parentAttribute1 = new UMLAttribute("-parentAttribute1", "String", "253da247-0593-4f3e-82bc-0a392b0fa3c4");
        UMLAttribute parentAttribute2 = new UMLAttribute("+parentAttribute2", "int", "423a328a-6b36-44f8-a249-0fb06b5d9261");
        UMLMethod parentMethod = new UMLMethod("+ parentMethod(): void", "+parentMethod", "void", emptyList(), "81a7258d-5730-4cbc-873a-f4c9d0ffd3a3");
        UMLClass abstractClass = new UMLClass("Abstract", List.of(parentAttribute1, parentAttribute2), List.of(parentMethod), "f2f222c5-7ecd-4c8f-8938-4b85a8760325",
                UMLClassType.ABSTRACT_CLASS);
        elements.add(parentAttribute1);
        elements.add(parentAttribute2);
        elements.add(parentMethod);
        elements.add(abstractClass);

        UMLAttribute childAttribute = new UMLAttribute("childAttribute", "", "c48cd77e-a743-4d54-a392-f89d4ac6651c");
        UMLMethod childMethod = new UMLMethod("childMethod(param1, param2)", "childMethod", "", List.of("param1", "param2"), "0cad1ae6-c447-4a7c-9579-4e8b9e4e6d2b");
        UMLClass class1 = new UMLClass("Class1", List.of(childAttribute), List.of(childMethod), "8efec96f-fb0a-41dc-ac6b-35b6c7bf5df3", UMLClassType.CLASS);
        elements.add(childAttribute);
        elements.add(childMethod);
        elements.add(class1);

        UMLClass class2 = new UMLClass("Class2", emptyList(), emptyList(), "fd464e1a-dae3-4839-b138-6c23dd5ac743", UMLClassType.CLASS);
        elements.add(class2);

        UMLAttribute case1 = new UMLAttribute("Case1", "", "72096033-e9b0-4edd-b66c-97c94811ffef");
        UMLAttribute case2 = new UMLAttribute("Case2", "", "d7ac25c9-3ea5-47e5-b131-15756079a430");
        UMLAttribute case3 = new UMLAttribute("Case3", "", "6b7bf0c3-ccfe-41c7-97bc-9c39e820a465");
        UMLClass enumClass = new UMLClass("Enumeration", List.of(case1, case2, case3), emptyList(), "51a749b9-6a04-498b-ae15-4e4b9c224570", UMLClassType.ENUMERATION);
        elements.add(case1);
        elements.add(case2);
        elements.add(case3);
        elements.add(enumClass);

        UMLPackage umlPackage = new UMLPackage("Package", List.of(abstractClass, class1, class2, enumClass), "150e772a-779b-4879-b0ac-677212dab6e0");
        elements.add(umlPackage);

        UMLRelationship realization = new UMLRelationship(class1, interfaceClass, CLASS_REALIZATION, "8b8172a5-0ff2-4880-8b99-2d74752a70c1", "", "", "", "");
        elements.add(realization);

        UMLRelationship inheritance = new UMLRelationship(class1, abstractClass, CLASS_INHERITANCE, "064abad0-5dab-412a-8df6-9ca2dd237fc8", "", "", "", "");
        elements.add(inheritance);

        UMLRelationship unidirectional = new UMLRelationship(class1, enumClass, CLASS_UNIDIRECTIONAL, "15c73879-52da-4a19-bb4e-661f1628c6fe", "", "enum", "", "");
        elements.add(unidirectional);

        UMLRelationship bidirectional = new UMLRelationship(class1, class2, CLASS_BIDIRECTIONAL, "e8270c39-4055-4e32-9486-3b9460e1c625", "sourceRole", "targetRole", "*", "0..1");
        elements.add(bidirectional);

        UMLRelationship dependency = new UMLRelationship(class2, abstractClass, CLASS_DEPENDENCY, "ff043204-2e61-4d59-a96b-4138f239a399", "", "", "", "");
        elements.add(dependency);

        UMLRelationship aggregation = new UMLRelationship(class2, class2, CLASS_AGGREGATION, "d0d64ca7-3970-4e52-b7a9-11c392d18878", "", "", "", "");
        elements.add(aggregation);

        UMLRelationship composition = new UMLRelationship(class2, class2, CLASS_COMPOSITION, "d7d9cbfa-35ec-4369-b419-20aefeb1555d", "", "", "", "");
        elements.add(composition);

        return elements;
    }

    private List<UMLElement> createExampleActivityDiagramElements() {
        List<UMLElement> elements = new ArrayList<>();

        UMLActivity activity1 = new UMLActivity("Activity1", emptyList(), "cfcb7b17-0500-4654-89bb-a7082d5c3b6d");
        elements.add(activity1);

        UMLActivityNode initialNode = new UMLActivityNode("", "2d1fbb07-bf49-43c1-a55f-35437e2cfcdc", ACTIVITY_INITIAL_NODE);
        elements.add(initialNode);

        UMLActivityNode finalNode = new UMLActivityNode("", "5019a714-3bef-4e4e-b649-b759c2b8778a", ACTIVITY_FINAL_NODE);
        elements.add(finalNode);

        UMLActivityNode decisionNode = new UMLActivityNode("Decision", "02c342c2-53d4-4bd7-9e08-774dbaa97ca6", ACTIVITY_MERGE_NODE);
        elements.add(decisionNode);

        UMLActivityNode mergeNode = new UMLActivityNode("Merge", "e81a38a1-9ceb-46c9-b9e4-f8d274c76085", ACTIVITY_MERGE_NODE);
        elements.add(mergeNode);

        UMLActivityNode forkNode = new UMLActivityNode("", "06c289b0-0a7d-4f9b-abef-3027a06330aa", ACTIVITY_FORK_NODE);
        elements.add(forkNode);

        UMLActivityNode actionNode1 = new UMLActivityNode("Action1", "1c0576f6-89b0-4b13-96d9-e1a0a227e8e1", ACTIVITY_ACTION_NODE);
        elements.add(actionNode1);

        UMLActivityNode actionNode2 = new UMLActivityNode("Action2", "056d0fe3-942a-4719-9b59-4c52443adcd5", ACTIVITY_ACTION_NODE);
        elements.add(actionNode2);

        UMLActivityNode objectNode = new UMLActivityNode("Object", "525b1697-6f48-4b07-9504-8feddb48a882", ACTIVITY_OBJECT_NODE);
        elements.add(objectNode);

        UMLActivity activity2 = new UMLActivity("Activity2", List.of(initialNode, finalNode, decisionNode, mergeNode, forkNode, actionNode1, actionNode2, objectNode),
                "f85b009b-3320-4c73-9c7b-0d4bbe7e74f9");
        elements.add(activity2);

        UMLControlFlow controlFlow1 = new UMLControlFlow(activity1, activity2, "fa0285e3-a3e6-4281-8c0e-223e5758106e");
        UMLControlFlow controlFlow2 = new UMLControlFlow(initialNode, decisionNode, "fa4fb017-4022-4b44-8caa-fe030173b8c5");
        UMLControlFlow controlFlow3 = new UMLControlFlow(decisionNode, mergeNode, "c479f73c-8125-40de-8e6b-0e67ea515307");
        UMLControlFlow controlFlow4 = new UMLControlFlow(decisionNode, forkNode, "50a3baf7-7678-446a-ac5b-e491210ea673");
        UMLControlFlow controlFlow5 = new UMLControlFlow(forkNode, actionNode1, "98368d15-25a3-4572-a6b6-3fc97e0d532f");
        UMLControlFlow controlFlow6 = new UMLControlFlow(forkNode, actionNode2, "7e59aded-6e55-4241-ac61-ab306ea23ecd");
        UMLControlFlow controlFlow7 = new UMLControlFlow(actionNode1, activity2, "9fb27fb7-153c-4938-92a7-3f79722cf5c0");
        UMLControlFlow controlFlow8 = new UMLControlFlow(actionNode2, activity2, "a14b46df-64aa-401a-87f1-82076ba20e9c");
        UMLControlFlow controlFlow9 = new UMLControlFlow(activity2, objectNode, "fa9b870d-2b4b-47e1-946a-abf365dc0f15");
        UMLControlFlow controlFlow10 = new UMLControlFlow(objectNode, mergeNode, "d268d8d8-8785-4f54-9777-4fa15e008728");
        UMLControlFlow controlFlow11 = new UMLControlFlow(mergeNode, finalNode, "84cb94c0-dc0a-4794-b8b1-83c44b3be13e");
        elements.add(controlFlow1);
        elements.add(controlFlow2);
        elements.add(controlFlow3);
        elements.add(controlFlow4);
        elements.add(controlFlow5);
        elements.add(controlFlow6);
        elements.add(controlFlow7);
        elements.add(controlFlow8);
        elements.add(controlFlow9);
        elements.add(controlFlow10);
        elements.add(controlFlow11);

        return elements;
    }
}
