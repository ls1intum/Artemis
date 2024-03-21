package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.bpmn;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.bpmn.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

class BPMNDiagramTest extends AbstractUMLDiagramTest {

    @Test
    void similarityBpmnDiagramEqualModels() {

        double minimumSimilarity = 0.8;
        double expectedSimilarity = 100.0;

        compareSubmissions(modelingSubmission(BPMNDiagrams.BPMN_MODEL_1), modelingSubmission(BPMNDiagrams.BPMN_MODEL_1), minimumSimilarity, expectedSimilarity);
        compareSubmissions(modelingSubmission(BPMNDiagrams.BPMN_MODEL_2), modelingSubmission(BPMNDiagrams.BPMN_MODEL_2), minimumSimilarity, expectedSimilarity);
    }

    @Test
    void similarityBpmnDiagramDifferentModels() {

        double minimumSimilarity = 0.0;
        double expectedSimilarity = 15.07;

        compareSubmissions(modelingSubmission(BPMNDiagrams.BPMN_MODEL_1), modelingSubmission(BPMNDiagrams.BPMN_MODEL_2), minimumSimilarity, expectedSimilarity);
        compareSubmissions(modelingSubmission(BPMNDiagrams.BPMN_MODEL_2), modelingSubmission(BPMNDiagrams.BPMN_MODEL_1), minimumSimilarity, expectedSimilarity);
    }

    @Test
    void parseDiagramElementsCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(BPMNDiagrams.BPMN_MODEL_3).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(BPMNDiagram.class);
        BPMNDiagram bpmnDiagram = (BPMNDiagram) diagram;

        assertThat(bpmnDiagram.getAllModelElements()).hasSize(15);
        assertThat(bpmnDiagram.getAnnotations()).hasSize(1);
        assertThat(bpmnDiagram.getCallActivities()).hasSize(1);
        assertThat(bpmnDiagram.getDataObjects()).hasSize(1);
        assertThat(bpmnDiagram.getDataStores()).hasSize(1);
        assertThat(bpmnDiagram.getEndEvents()).hasSize(1);
        assertThat(bpmnDiagram.getGateways()).hasSize(1);
        assertThat(bpmnDiagram.getGroups()).hasSize(1);
        assertThat(bpmnDiagram.getIntermediateEvents()).hasSize(1);
        assertThat(bpmnDiagram.getPools()).hasSize(1);
        assertThat(bpmnDiagram.getStartEvents()).hasSize(1);
        assertThat(bpmnDiagram.getSubprocesses()).hasSize(1);
        assertThat(bpmnDiagram.getSwimlanes()).hasSize(2);
        assertThat(bpmnDiagram.getTasks()).hasSize(1);
        assertThat(bpmnDiagram.getTransactions()).hasSize(1);

        UMLElement taskElement = bpmnDiagram.getElementByJSONID("24e6430e-e7d7-4065-81ae-3575ed168940");
        assertThat(taskElement).isInstanceOf(BPMNTask.class);
        BPMNTask task = (BPMNTask) taskElement;
        assertThat(task.getType()).isEqualTo("BPMNTask");
        assertThat(task.getName()).isEqualTo("Task");
        assertThat(task.toString()).isEqualTo("Task");
        assertThat(task.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(task.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);
        assertThat(task.similarity(task)).isEqualTo(1.0);

        UMLElement subprocessElement = bpmnDiagram.getElementByJSONID("9c9b76c8-8e06-4aa5-ab10-ff6e376c9566");
        assertThat(subprocessElement).isInstanceOf(BPMNSubprocess.class);
        BPMNSubprocess subprocess = (BPMNSubprocess) subprocessElement;
        assertThat(subprocess.getType()).isEqualTo("BPMNSubprocess");
        assertThat(subprocess.getName()).isEqualTo("Subprocess");
        assertThat(subprocess.toString()).isEqualTo("Subprocess");
        assertThat(subprocess.similarity(subprocess)).isEqualTo(1.0);

        UMLElement transactionElement = bpmnDiagram.getElementByJSONID("3d81a5cd-8541-4532-a72c-5ae4dba48eea");
        assertThat(transactionElement).isInstanceOf(BPMNTransaction.class);
        BPMNTransaction transaction = (BPMNTransaction) transactionElement;
        assertThat(transaction.getType()).isEqualTo("BPMNTransaction");
        assertThat(transaction.getName()).isEqualTo("Transaction");
        assertThat(transaction.toString()).isEqualTo("Transaction");
        assertThat(transaction.similarity(transaction)).isEqualTo(1.0);

        UMLElement callActivityElement = bpmnDiagram.getElementByJSONID("b5eddff7-b20c-4181-be3a-6272d5192783");
        assertThat(callActivityElement).isInstanceOf(BPMNCallActivity.class);
        BPMNCallActivity callActivity = (BPMNCallActivity) callActivityElement;
        assertThat(callActivity.getType()).isEqualTo("BPMNCallActivity");
        assertThat(callActivity.getName()).isEqualTo("Call Activity");
        assertThat(callActivity.toString()).isEqualTo("Call Activity");
        assertThat(callActivity.similarity(callActivity)).isEqualTo(1.0);

        UMLElement annotationElement = bpmnDiagram.getElementByJSONID("977df2b3-d39e-434a-b364-d058b6d265e5");
        assertThat(annotationElement).isInstanceOf(BPMNAnnotation.class);
        BPMNAnnotation annotation = (BPMNAnnotation) annotationElement;
        assertThat(annotation.getType()).isEqualTo("BPMNAnnotation");
        assertThat(annotation.getName()).isEqualTo("Annotation");
        assertThat(annotation.toString()).isEqualTo("Annotation");
        assertThat(annotation.similarity(annotation)).isEqualTo(1.0);

        UMLElement startEventElement = bpmnDiagram.getElementByJSONID("68d1f3b2-3970-44f8-8f37-e34fea4485bb");
        assertThat(startEventElement).isInstanceOf(BPMNStartEvent.class);
        BPMNStartEvent startEvent = (BPMNStartEvent) startEventElement;
        assertThat(startEvent.getType()).isEqualTo("BPMNStartEvent");
        assertThat(startEvent.getName()).isEqualTo("");
        assertThat(startEvent.toString()).isEqualTo("");
        assertThat(startEvent.getEventType()).isEqualTo(BPMNStartEvent.BPMNStartEventType.DEFAULT);
        assertThat(startEvent.similarity(startEvent)).isEqualTo(1.0);

        UMLElement intermediateEventElement = bpmnDiagram.getElementByJSONID("3b7f912b-cfc6-4dc5-a6de-0dbe0507c36b");
        assertThat(intermediateEventElement).isInstanceOf(BPMNIntermediateEvent.class);
        BPMNIntermediateEvent intermediateEvent = (BPMNIntermediateEvent) intermediateEventElement;
        assertThat(intermediateEvent.getType()).isEqualTo("BPMNIntermediateEvent");
        assertThat(intermediateEvent.getName()).isEqualTo("");
        assertThat(intermediateEvent.toString()).isEqualTo("");
        assertThat(intermediateEvent.getEventType()).isEqualTo(BPMNIntermediateEvent.BPMNIntermediateEventType.DEFAULT);
        assertThat(intermediateEvent.similarity(intermediateEvent)).isEqualTo(1.0);

        UMLElement endEventElement = bpmnDiagram.getElementByJSONID("b2402d3a-525e-4a66-8385-bc14ff548475");
        assertThat(endEventElement).isInstanceOf(BPMNEndEvent.class);
        BPMNEndEvent endEvent = (BPMNEndEvent) endEventElement;
        assertThat(endEvent.getType()).isEqualTo("BPMNEndEvent");
        assertThat(endEvent.getName()).isEqualTo("");
        assertThat(endEvent.toString()).isEqualTo("");
        assertThat(endEvent.getEventType()).isEqualTo(BPMNEndEvent.BPMNEndEventType.DEFAULT);
        assertThat(endEvent.similarity(endEvent)).isEqualTo(1.0);

        UMLElement gatewayElement = bpmnDiagram.getElementByJSONID("730a9658-0a60-4fa9-8890-81788cb8a641");
        assertThat(gatewayElement).isInstanceOf(BPMNGateway.class);
        BPMNGateway gateway = (BPMNGateway) gatewayElement;
        assertThat(gateway.getType()).isEqualTo("BPMNGateway");
        assertThat(gateway.getName()).isEqualTo("");
        assertThat(gateway.toString()).isEqualTo("");
        assertThat(gateway.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EXCLUSIVE);
        assertThat(gateway.similarity(gateway)).isEqualTo(1.0);

        UMLElement dataObjectElement = bpmnDiagram.getElementByJSONID("94b72e34-5eb5-4a32-b098-3bc38a389266");
        assertThat(dataObjectElement).isInstanceOf(BPMNDataObject.class);
        BPMNDataObject dataObject = (BPMNDataObject) dataObjectElement;
        assertThat(dataObject.getType()).isEqualTo("BPMNDataObject");
        assertThat(dataObject.getName()).isEqualTo("");
        assertThat(dataObject.toString()).isEqualTo("");
        assertThat(dataObject.similarity(dataObject)).isEqualTo(1.0);

        UMLElement dataStoreElement = bpmnDiagram.getElementByJSONID("7a9aaa87-b828-4244-9692-319b35ec4fbf");
        assertThat(dataStoreElement).isInstanceOf(BPMNDataStore.class);
        BPMNDataStore dataStore = (BPMNDataStore) dataStoreElement;
        assertThat(dataStore.getType()).isEqualTo("BPMNDataStore");
        assertThat(dataStore.getName()).isEqualTo("");
        assertThat(dataStore.toString()).isEqualTo("");
        assertThat(dataStore.similarity(dataStore)).isEqualTo(1.0);

        UMLElement groupElement = bpmnDiagram.getElementByJSONID("b12777af-5198-4785-adcb-b783d3897236");
        assertThat(groupElement).isInstanceOf(BPMNGroup.class);
        BPMNGroup group = (BPMNGroup) groupElement;
        assertThat(group.getType()).isEqualTo("BPMNGroup");
        assertThat(group.getName()).isEqualTo("");
        assertThat(group.toString()).isEqualTo("");
        assertThat(group.similarity(group)).isEqualTo(1.0);

        UMLElement poolElement = bpmnDiagram.getElementByJSONID("9cf8b22d-62f0-4f29-b354-007e89128729");
        assertThat(poolElement).isInstanceOf(BPMNPool.class);
        BPMNPool pool = (BPMNPool) poolElement;
        assertThat(pool.getType()).isEqualTo("BPMNPool");
        assertThat(pool.getName()).isEqualTo("Pool");
        assertThat(pool.toString()).isEqualTo("Pool");
        assertThat(pool.similarity(pool)).isEqualTo(1.0);

        UMLElement swimlaneElement1 = bpmnDiagram.getElementByJSONID("9451d510-b382-45ff-af78-14952d5dc2a0");
        assertThat(swimlaneElement1).isInstanceOf(BPMNSwimlane.class);
        BPMNSwimlane swimlane1 = (BPMNSwimlane) swimlaneElement1;
        assertThat(swimlane1.getType()).isEqualTo("BPMNSwimlane");
        assertThat(swimlane1.getName()).isEqualTo("Lane 1");
        assertThat(swimlane1.toString()).isEqualTo("Lane 1");
        assertThat(swimlane1.getParentElement()).isSameAs(pool);
        assertThat(swimlane1.similarity(swimlane1)).isEqualTo(1.0);

        UMLElement swimlaneElement2 = bpmnDiagram.getElementByJSONID("03da0f2b-c915-4881-a7e0-90e8e5a0dad8");
        assertThat(swimlaneElement2).isInstanceOf(BPMNSwimlane.class);
        BPMNSwimlane swimlane2 = (BPMNSwimlane) swimlaneElement2;
        assertThat(swimlane2.getType()).isEqualTo("BPMNSwimlane");
        assertThat(swimlane2.getName()).isEqualTo("Lane 2");
        assertThat(swimlane2.toString()).isEqualTo("Lane 2");
        assertThat(swimlane2.getParentElement()).isSameAs(pool);
        assertThat(swimlane2.similarity(swimlane2)).isEqualTo(1.0);
        assertThat(swimlane1.similarity(swimlane2)).isEqualTo(0.83);

    }

    @Test
    void parseLargeBpmnDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(BPMNDiagrams.BPMN_MODEL_2).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(BPMNDiagram.class);
        BPMNDiagram bpmnDiagram = (BPMNDiagram) diagram;

        // -------------------------------------------------------------------------------------------------------------

        // Check if the expected number of elements is present
        assertThat(bpmnDiagram.getAllModelElements()).hasSize(44);
        assertThat(bpmnDiagram.getAnnotations()).hasSize(0);
        assertThat(bpmnDiagram.getCallActivities()).hasSize(0);
        assertThat(bpmnDiagram.getDataObjects()).hasSize(0);
        assertThat(bpmnDiagram.getDataStores()).hasSize(1);
        assertThat(bpmnDiagram.getEndEvents()).hasSize(2);
        assertThat(bpmnDiagram.getGateways()).hasSize(6);
        assertThat(bpmnDiagram.getGroups()).hasSize(0);
        assertThat(bpmnDiagram.getIntermediateEvents()).hasSize(2);
        assertThat(bpmnDiagram.getPools()).hasSize(2);
        assertThat(bpmnDiagram.getStartEvents()).hasSize(1);
        assertThat(bpmnDiagram.getSubprocesses()).hasSize(0);
        assertThat(bpmnDiagram.getSwimlanes()).hasSize(0);
        assertThat(bpmnDiagram.getTasks()).hasSize(7);
        assertThat(bpmnDiagram.getTransactions()).hasSize(0);

        // -------------------------------------------------------------------------------------------------------------

        // Check the correct types and properties of elements

        // BPMNPool

        UMLElement applicantPoolElement = bpmnDiagram.getElementByJSONID("a6436536-2089-4aac-804d-6fc17ed297ff");
        assertThat(applicantPoolElement).isInstanceOf(BPMNPool.class);
        BPMNPool applicantPool = (BPMNPool) applicantPoolElement;
        assertThat(applicantPool.getName()).isEqualTo("Applicant");
        assertThat(applicantPool.getParentElement()).isNull();

        UMLElement hrPoolElement = bpmnDiagram.getElementByJSONID("2b9f1df6-45bd-422e-97f6-93e11f279143");
        assertThat(hrPoolElement).isInstanceOf(BPMNPool.class);
        BPMNPool hrPool = (BPMNPool) hrPoolElement;
        assertThat(hrPool.getName()).isEqualTo("HR Department");
        assertThat(hrPool.getParentElement()).isNull();

        // BPMNDataStore
        UMLElement dataStoreElement = bpmnDiagram.getElementByJSONID("e70c2c47-b5d3-40e6-a868-2f9df7c7cdfd");
        assertThat(dataStoreElement).isInstanceOf(BPMNDataStore.class);
        BPMNDataStore dataStore = (BPMNDataStore) dataStoreElement;
        assertThat(dataStore.getName()).isEqualTo("");
        assertThat(dataStore.getParentElement()).isNull();

        // BPMNEndEvent
        UMLElement applicationReturnedEndEventElement = bpmnDiagram.getElementByJSONID("37539d43-8bda-4cdd-867e-73f18232ff9e");
        assertThat(applicationReturnedEndEventElement).isInstanceOf(BPMNEndEvent.class);
        BPMNEndEvent applicationReturnedEndEvent = (BPMNEndEvent) applicationReturnedEndEventElement;
        assertThat(applicationReturnedEndEvent.getName()).isEqualTo("Application returned");
        assertThat(applicationReturnedEndEvent.getParentElement()).isSameAs(hrPoolElement);
        assertThat(applicationReturnedEndEvent.getEventType()).isEqualTo(BPMNEndEvent.BPMNEndEventType.DEFAULT);

        UMLElement applicationProcessedEndEventElement = bpmnDiagram.getElementByJSONID("9df2dc71-39a0-4ef8-a72c-61a54c4287af");
        assertThat(applicationProcessedEndEventElement).isInstanceOf(BPMNEndEvent.class);
        BPMNEndEvent applicationProcessedEndEvent = (BPMNEndEvent) applicationProcessedEndEventElement;
        assertThat(applicationProcessedEndEvent.getName()).isEqualTo("Application processed");
        assertThat(applicationProcessedEndEvent.getParentElement()).isSameAs(hrPoolElement);
        assertThat(applicationReturnedEndEvent.getEventType()).isEqualTo(BPMNEndEvent.BPMNEndEventType.DEFAULT);

        // BPMNGateway
        UMLElement genericExclusiveGatewayElement1 = bpmnDiagram.getElementByJSONID("290cc1ab-2910-4fe3-8ea6-dbcf0a70e4e8");
        assertThat(genericExclusiveGatewayElement1).isInstanceOf(BPMNGateway.class);
        BPMNGateway genericExclusiveGateway1 = (BPMNGateway) genericExclusiveGatewayElement1;
        assertThat(genericExclusiveGateway1.getName()).isEqualTo("");
        assertThat(genericExclusiveGateway1.getParentElement()).isSameAs(hrPoolElement);
        assertThat(genericExclusiveGateway1.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EXCLUSIVE);

        UMLElement genericExclusiveGatewayElement2 = bpmnDiagram.getElementByJSONID("81dd9f77-17bc-4f89-a8d8-03b70175eb63");
        assertThat(genericExclusiveGatewayElement2).isInstanceOf(BPMNGateway.class);
        BPMNGateway genericExclusiveGateway2 = (BPMNGateway) genericExclusiveGatewayElement2;
        assertThat(genericExclusiveGateway2.getName()).isEqualTo("");
        assertThat(genericExclusiveGateway2.getParentElement()).isSameAs(hrPoolElement);
        assertThat(genericExclusiveGateway2.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EXCLUSIVE);

        UMLElement genericEventBasedGatewayElement1 = bpmnDiagram.getElementByJSONID("fd739bfc-a881-4186-a5db-11aba3f16003");
        assertThat(genericEventBasedGatewayElement1).isInstanceOf(BPMNGateway.class);
        BPMNGateway genericEventBasedGateway1 = (BPMNGateway) genericEventBasedGatewayElement1;
        assertThat(genericEventBasedGateway1.getName()).isEqualTo("");
        assertThat(genericEventBasedGateway1.getParentElement()).isSameAs(hrPoolElement);
        assertThat(genericEventBasedGateway1.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EVENT_BASED);

        UMLElement applicationVoidExclusiveGatewayElement = bpmnDiagram.getElementByJSONID("cf8c258d-09dc-4d66-876d-5c8d5fb01b3f");
        assertThat(applicationVoidExclusiveGatewayElement).isInstanceOf(BPMNGateway.class);
        BPMNGateway applicationVoidExclusiveGateway = (BPMNGateway) applicationVoidExclusiveGatewayElement;
        assertThat(applicationVoidExclusiveGateway.getName()).isEqualTo("Application void?");
        assertThat(applicationVoidExclusiveGateway.getParentElement()).isSameAs(hrPoolElement);
        assertThat(applicationVoidExclusiveGateway.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EXCLUSIVE);

        UMLElement applicationIncompleteExclusiveGatewayElement = bpmnDiagram.getElementByJSONID("3f507a61-445c-4096-868c-aee193532800");
        assertThat(applicationIncompleteExclusiveGatewayElement).isInstanceOf(BPMNGateway.class);
        BPMNGateway applicationIncompleteExclusiveGateway = (BPMNGateway) applicationIncompleteExclusiveGatewayElement;
        assertThat(applicationIncompleteExclusiveGateway.getName()).isEqualTo("Is application incomplete?");
        assertThat(applicationIncompleteExclusiveGateway.getParentElement()).isSameAs(hrPoolElement);
        assertThat(applicationIncompleteExclusiveGateway.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EXCLUSIVE);

        UMLElement applicationContentsOkExclusiveGatewayElement = bpmnDiagram.getElementByJSONID("fd46d300-8a0a-4f5e-81d0-e2a3e92bbffa");
        assertThat(applicationContentsOkExclusiveGatewayElement).isInstanceOf(BPMNGateway.class);
        BPMNGateway applicationContentsOkExclusiveGateway = (BPMNGateway) applicationContentsOkExclusiveGatewayElement;
        assertThat(applicationContentsOkExclusiveGateway.getName()).isEqualTo("Are application contents ok?");
        assertThat(applicationContentsOkExclusiveGateway.getParentElement()).isSameAs(hrPoolElement);
        assertThat(applicationContentsOkExclusiveGateway.getGatewayType()).isEqualTo(BPMNGateway.BPMNGatewayType.EXCLUSIVE);

        // BPMNtIntermediateEvent

        UMLElement timeExpiredIntermediateEventElement = bpmnDiagram.getElementByJSONID("6f9c91e5-da06-45fa-9ea6-f124f0d12f37");
        assertThat(timeExpiredIntermediateEventElement).isInstanceOf(BPMNIntermediateEvent.class);
        BPMNIntermediateEvent timeExpiredIntermediateEvent = (BPMNIntermediateEvent) timeExpiredIntermediateEventElement;
        assertThat(timeExpiredIntermediateEvent.getName()).isEqualTo("Response time expired");
        assertThat(timeExpiredIntermediateEvent.getParentElement()).isSameAs(hrPoolElement);
        assertThat(timeExpiredIntermediateEvent.getEventType()).isEqualTo(BPMNIntermediateEvent.BPMNIntermediateEventType.TIMER_CATCH);

        UMLElement additionalInformationIntermediateEventElement = bpmnDiagram.getElementByJSONID("2abce597-eabd-41f0-b2f9-c301162e4294");
        assertThat(additionalInformationIntermediateEventElement).isInstanceOf(BPMNIntermediateEvent.class);
        BPMNIntermediateEvent additionalInformationIntermediateEvent = (BPMNIntermediateEvent) additionalInformationIntermediateEventElement;
        assertThat(additionalInformationIntermediateEvent.getName()).isEqualTo("Receive additional information");
        assertThat(additionalInformationIntermediateEvent.getParentElement()).isSameAs(hrPoolElement);
        assertThat(additionalInformationIntermediateEvent.getEventType()).isEqualTo(BPMNIntermediateEvent.BPMNIntermediateEventType.MESSAGE_CATCH);

        // BPMNStartEvent

        UMLElement startEventElement = bpmnDiagram.getElementByJSONID("de8c42dd-1064-4c9f-a6ab-386c26f99bc0");
        assertThat(startEventElement).isInstanceOf(BPMNStartEvent.class);
        BPMNStartEvent startEvent = (BPMNStartEvent) startEventElement;
        assertThat(startEvent.getName()).isEqualTo("Application received");
        assertThat(startEvent.getParentElement()).isSameAs(hrPoolElement);
        assertThat(startEvent.getEventType()).isEqualTo(BPMNStartEvent.BPMNStartEventType.MESSAGE);

        // BPMNTask

        UMLElement applicationCompleteTaskElement = bpmnDiagram.getElementByJSONID("d3184916-e518-45ac-87ca-259ad61e2562");
        assertThat(applicationCompleteTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask applicationCompleteTask = (BPMNTask) applicationCompleteTaskElement;
        assertThat(applicationCompleteTask.getName()).isEqualTo("Check if application is complete");
        assertThat(applicationCompleteTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(applicationCompleteTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(applicationCompleteTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        UMLElement checkContentsTaskElement = bpmnDiagram.getElementByJSONID("aca9d000-2017-4301-8d46-fbc3b5a84ce4");
        assertThat(checkContentsTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask checkContentsTask = (BPMNTask) checkContentsTaskElement;
        assertThat(checkContentsTask.getName()).isEqualTo("Check contents of application");
        assertThat(checkContentsTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(checkContentsTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(checkContentsTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        UMLElement registerApplicationTaskElement = bpmnDiagram.getElementByJSONID("103f9247-a641-47c7-bced-0fc6c432ae4e");
        assertThat(registerApplicationTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask registerApplicationTask = (BPMNTask) registerApplicationTaskElement;
        assertThat(registerApplicationTask.getName()).isEqualTo("Register application");
        assertThat(registerApplicationTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(registerApplicationTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(registerApplicationTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        UMLElement archiveApplicationTaskElement = bpmnDiagram.getElementByJSONID("501cca0c-4020-46ea-a45a-a32d87e888c4");
        assertThat(archiveApplicationTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask archiveApplicationTask = (BPMNTask) archiveApplicationTaskElement;
        assertThat(archiveApplicationTask.getName()).isEqualTo("Archive application");
        assertThat(archiveApplicationTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(archiveApplicationTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(archiveApplicationTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        UMLElement sendResponseTaskElement = bpmnDiagram.getElementByJSONID("d97e718f-52ef-4d95-b232-e8f387b4eaa3");
        assertThat(sendResponseTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask sendResponseTask = (BPMNTask) sendResponseTaskElement;
        assertThat(sendResponseTask.getName()).isEqualTo("Send response");
        assertThat(sendResponseTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(sendResponseTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(sendResponseTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        UMLElement followUpTaskElement = bpmnDiagram.getElementByJSONID("557f16e0-a74b-45e8-a2ea-17fb5aecb853");
        assertThat(followUpTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask followUpTask = (BPMNTask) followUpTaskElement;
        assertThat(followUpTask.getName()).isEqualTo("Follow up with applicant");
        assertThat(followUpTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(followUpTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(followUpTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        UMLElement returnApplicationTaskElement = bpmnDiagram.getElementByJSONID("cc627472-a35f-41c6-8f23-a27c0aed0075");
        assertThat(returnApplicationTaskElement).isInstanceOf(BPMNTask.class);
        BPMNTask returnApplicationTask = (BPMNTask) returnApplicationTaskElement;
        assertThat(returnApplicationTask.getName()).isEqualTo("Return application");
        assertThat(returnApplicationTask.getParentElement()).isSameAs(hrPoolElement);
        assertThat(returnApplicationTask.getTaskType()).isEqualTo(BPMNTask.BPMNTaskType.DEFAULT);
        assertThat(returnApplicationTask.getMarker()).isEqualTo(BPMNTask.BPMNMarker.NONE);

        // -------------------------------------------------------------------------------------------------------------

        // BPMNFlows

        // Check if the expected number of elements is present
        assertThat(bpmnDiagram.getFlows()).hasSize(23);

        // -------------------------------------------------------------------------------------------------------------

        // Sequence flows

        // startEvent -----> genericExclusiveGateway1
        UMLElement sequenceFlowElement1 = bpmnDiagram.getElementByJSONID("bcb1376b-8361-4231-a008-6512275e8968");
        assertThat(sequenceFlowElement1).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow1 = (BPMNFlow) sequenceFlowElement1;
        assertThat(sequenceFlow1.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow1.getSource()).isSameAs(startEvent);
        assertThat(sequenceFlow1.getTarget()).isSameAs(genericExclusiveGateway1);
        assertThat(sequenceFlow1.getName()).isEqualTo("");

        // genericExclusiveGateway1 -----> applicationCompleteTask
        UMLElement sequenceFlowElement2 = bpmnDiagram.getElementByJSONID("4dca6255-7a1f-4d15-8879-f5844ff051a9");
        assertThat(sequenceFlowElement2).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow2 = (BPMNFlow) sequenceFlowElement2;
        assertThat(sequenceFlow2.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow2.getSource()).isSameAs(genericExclusiveGateway1);
        assertThat(sequenceFlow2.getTarget()).isSameAs(applicationCompleteTask);
        assertThat(sequenceFlow2.getName()).isEqualTo("");

        // applicationCompleteTask -----> applicationIncompleteExclusiveGateway
        UMLElement sequenceFlowElement3 = bpmnDiagram.getElementByJSONID("14fbaae2-44f5-43d9-b949-705ab05d695b");
        assertThat(sequenceFlowElement3).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow3 = (BPMNFlow) sequenceFlowElement3;
        assertThat(sequenceFlow3.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow3.getSource()).isSameAs(applicationCompleteTask);
        assertThat(sequenceFlow3.getTarget()).isSameAs(applicationIncompleteExclusiveGateway);
        assertThat(sequenceFlow3.getName()).isEqualTo("");

        // applicationIncompleteExclusiveGateway ----> checkContentsTask
        UMLElement sequenceFlowElement4 = bpmnDiagram.getElementByJSONID("1a406709-6b4b-4875-b399-f88b6b2a1795");
        assertThat(sequenceFlowElement4).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow4 = (BPMNFlow) sequenceFlowElement4;
        assertThat(sequenceFlow4.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow4.getSource()).isSameAs(applicationIncompleteExclusiveGateway);
        assertThat(sequenceFlow4.getTarget()).isSameAs(checkContentsTask);
        assertThat(sequenceFlow4.getName()).isEqualTo("No");

        // checkContentsTask -----> applicationContentsOkExclusiveGateway
        UMLElement sequenceFlowElement5 = bpmnDiagram.getElementByJSONID("d4f7c0aa-211d-4b79-ba6b-0ee208098c38");
        assertThat(sequenceFlowElement5).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow5 = (BPMNFlow) sequenceFlowElement5;
        assertThat(sequenceFlow5.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow5.getSource()).isSameAs(checkContentsTask);
        assertThat(sequenceFlow5.getTarget()).isSameAs(applicationContentsOkExclusiveGateway);
        assertThat(sequenceFlow5.getName()).isEqualTo("");

        // applicationContentsOkExclusiveGateway -----> registerApplicationTask
        UMLElement sequenceFlowElement6 = bpmnDiagram.getElementByJSONID("4fe17f38-1f2e-420f-b73a-def7d8aeae10");
        assertThat(sequenceFlowElement6).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow6 = (BPMNFlow) sequenceFlowElement6;
        assertThat(sequenceFlow6.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow6.getSource()).isSameAs(applicationContentsOkExclusiveGateway);
        assertThat(sequenceFlow6.getTarget()).isSameAs(registerApplicationTask);
        assertThat(sequenceFlow6.getName()).isEqualTo("Yes");

        // registerApplicationTask -----> archiveApplicationTask
        UMLElement sequenceFlowElement7 = bpmnDiagram.getElementByJSONID("6c8fa577-6039-492d-aa9c-11e91d37fdd9");
        assertThat(sequenceFlowElement7).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow7 = (BPMNFlow) sequenceFlowElement7;
        assertThat(sequenceFlow7.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow7.getSource()).isSameAs(registerApplicationTask);
        assertThat(sequenceFlow7.getTarget()).isSameAs(archiveApplicationTask);
        assertThat(sequenceFlow7.getName()).isEqualTo("");

        // archiveApplicationTask ----> applicationProcessedEndEvent
        UMLElement sequenceFlowElement8 = bpmnDiagram.getElementByJSONID("9ec3f03e-f160-4d71-a61f-f20bc138c9d5");
        assertThat(sequenceFlowElement8).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow8 = (BPMNFlow) sequenceFlowElement8;
        assertThat(sequenceFlow8.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow8.getSource()).isSameAs(archiveApplicationTask);
        assertThat(sequenceFlow8.getTarget()).isSameAs(applicationProcessedEndEvent);
        assertThat(sequenceFlow8.getName()).isEqualTo("");

        // additionalInformationIntermediateEvent -----> genericExclusiveGateway1
        UMLElement sequenceFlowElement9 = bpmnDiagram.getElementByJSONID("e300ceb6-3d31-4045-9c1b-f644fa651977");
        assertThat(sequenceFlowElement9).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow9 = (BPMNFlow) sequenceFlowElement9;
        assertThat(sequenceFlow9.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow9.getSource()).isSameAs(additionalInformationIntermediateEvent);
        assertThat(sequenceFlow9.getTarget()).isSameAs(genericExclusiveGateway1);
        assertThat(sequenceFlow9.getName()).isEqualTo("");

        // applicationIncompleteExclusiveGateway ----> sendResponseTask
        UMLElement sequenceFlowElement10 = bpmnDiagram.getElementByJSONID("d1fe7ccd-5383-4d79-9ece-4c8dd8110112");
        assertThat(sequenceFlowElement10).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow10 = (BPMNFlow) sequenceFlowElement10;
        assertThat(sequenceFlow10.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow10.getSource()).isSameAs(applicationIncompleteExclusiveGateway);
        assertThat(sequenceFlow10.getTarget()).isSameAs(sendResponseTask);
        assertThat(sequenceFlow10.getName()).isEqualTo("Yes");

        // sendResponseTask -----> genericEventBasedGateway1
        UMLElement sequenceFlowElement11 = bpmnDiagram.getElementByJSONID("3736b36b-0b9c-426c-b181-32d3917aab08");
        assertThat(sequenceFlowElement11).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow11 = (BPMNFlow) sequenceFlowElement11;
        assertThat(sequenceFlow11.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow11.getSource()).isSameAs(sendResponseTask);
        assertThat(sequenceFlow11.getTarget()).isSameAs(genericEventBasedGateway1);
        assertThat(sequenceFlow11.getName()).isEqualTo("");

        // genericEventBasedGateway1 -----> additionalInformationIntermediateEvent
        UMLElement sequenceFlowElement12 = bpmnDiagram.getElementByJSONID("88e3a977-0136-4688-a6ee-cea36031b2b0");
        assertThat(sequenceFlowElement12).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow12 = (BPMNFlow) sequenceFlowElement12;
        assertThat(sequenceFlow12.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow12.getSource()).isSameAs(genericEventBasedGateway1);
        assertThat(sequenceFlow12.getTarget()).isSameAs(additionalInformationIntermediateEvent);
        assertThat(sequenceFlow12.getName()).isEqualTo("");

        // applicationContentsOkExclusiveGateway -----> genericExclusiveGateway2
        UMLElement sequenceFlowElement13 = bpmnDiagram.getElementByJSONID("e5db4585-c354-4b1a-9f05-a4c5eac02e81");
        assertThat(sequenceFlowElement13).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow13 = (BPMNFlow) sequenceFlowElement13;
        assertThat(sequenceFlow13.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow13.getSource()).isSameAs(applicationContentsOkExclusiveGateway);
        assertThat(sequenceFlow13.getTarget()).isSameAs(genericExclusiveGateway2);
        assertThat(sequenceFlow13.getName()).isEqualTo("No");

        // genericEventBasedGateway1 -----> timeExpiredIntermediateEvent
        UMLElement sequenceFlowElement14 = bpmnDiagram.getElementByJSONID("2690e9f7-da44-4b9b-9ea9-4f00e4c196a2");
        assertThat(sequenceFlowElement14).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow14 = (BPMNFlow) sequenceFlowElement14;
        assertThat(sequenceFlow14.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow14.getSource()).isSameAs(genericEventBasedGateway1);
        assertThat(sequenceFlow14.getTarget()).isSameAs(timeExpiredIntermediateEvent);
        assertThat(sequenceFlow14.getName()).isEqualTo("");

        // timeExpiredIntermediateEvent ----> followUpTask
        UMLElement sequenceFlowElement15 = bpmnDiagram.getElementByJSONID("99cdfef0-0eee-45c6-892c-569d64e08d81");
        assertThat(sequenceFlowElement15).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow15 = (BPMNFlow) sequenceFlowElement15;
        assertThat(sequenceFlow15.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow15.getSource()).isSameAs(timeExpiredIntermediateEvent);
        assertThat(sequenceFlow15.getTarget()).isSameAs(followUpTask);
        assertThat(sequenceFlow15.getName()).isEqualTo("");

        // followUpTask -----> applicationVoidExclusiveGateway
        UMLElement sequenceFlowElement16 = bpmnDiagram.getElementByJSONID("7e0c061c-8ad4-4111-8539-4788cfcafd9d");
        assertThat(sequenceFlowElement16).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow16 = (BPMNFlow) sequenceFlowElement16;
        assertThat(sequenceFlow16.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow16.getSource()).isSameAs(followUpTask);
        assertThat(sequenceFlow16.getTarget()).isSameAs(applicationVoidExclusiveGateway);
        assertThat(sequenceFlow16.getName()).isEqualTo("Yes");

        // applicationVoidExclusiveGateway -----> genericEventBasedGateway1
        UMLElement sequenceFlowElement17 = bpmnDiagram.getElementByJSONID("5432779f-cd66-4d37-9b42-1ec121ecbf7d");
        assertThat(sequenceFlowElement17).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow17 = (BPMNFlow) sequenceFlowElement17;
        assertThat(sequenceFlow17.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow17.getSource()).isSameAs(applicationVoidExclusiveGateway);
        assertThat(sequenceFlow17.getTarget()).isSameAs(genericEventBasedGateway1);
        assertThat(sequenceFlow17.getName()).isEqualTo("No");

        // applicationVoidExclusiveGateway -----> genericExclusiveGateway2
        UMLElement sequenceFlowElement18 = bpmnDiagram.getElementByJSONID("47b4dcd2-6a47-4332-b113-007aa76425be");
        assertThat(sequenceFlowElement18).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow18 = (BPMNFlow) sequenceFlowElement18;
        assertThat(sequenceFlow18.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow18.getSource()).isSameAs(applicationVoidExclusiveGateway);
        assertThat(sequenceFlow18.getTarget()).isSameAs(genericExclusiveGateway2);
        assertThat(sequenceFlow18.getName()).isEqualTo("");

        // genericExclusiveGateway2 -----> returnApplicationTask
        UMLElement sequenceFlowElement19 = bpmnDiagram.getElementByJSONID("293508d1-1d29-4fa6-94ef-b417eb5766af");
        assertThat(sequenceFlowElement19).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow19 = (BPMNFlow) sequenceFlowElement19;
        assertThat(sequenceFlow19.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow19.getSource()).isSameAs(genericExclusiveGateway2);
        assertThat(sequenceFlow19.getTarget()).isSameAs(returnApplicationTask);
        assertThat(sequenceFlow19.getName()).isEqualTo("");

        // returnApplicationTask -----> applicationReturnedEndEvent
        UMLElement sequenceFlowElement20 = bpmnDiagram.getElementByJSONID("5643de18-0c63-4000-96af-70df7997f40d");
        assertThat(sequenceFlowElement20).isInstanceOf(BPMNFlow.class);
        BPMNFlow sequenceFlow20 = (BPMNFlow) sequenceFlowElement20;
        assertThat(sequenceFlow20.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.SEQUENCE);
        assertThat(sequenceFlow20.getSource()).isSameAs(returnApplicationTask);
        assertThat(sequenceFlow20.getTarget()).isSameAs(applicationReturnedEndEvent);
        assertThat(sequenceFlow20.getName()).isEqualTo("");

        // -------------------------------------------------------------------------------------------------------------

        // Message flows

        // sendResponseTask - - -> applicantPool
        UMLElement messageFlowElement1 = bpmnDiagram.getElementByJSONID("49b7b148-2c50-43b1-aca8-2b90253ae0bd");
        assertThat(messageFlowElement1).isInstanceOf(BPMNFlow.class);
        BPMNFlow messageFlow1 = (BPMNFlow) messageFlowElement1;
        assertThat(messageFlow1.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.MESSAGE);
        assertThat(messageFlow1.getSource()).isSameAs(sendResponseTask);
        assertThat(messageFlow1.getTarget()).isSameAs(applicantPool);
        assertThat(messageFlow1.getName()).isEqualTo("");

        // followUpTask - - -> applicantPool
        UMLElement messageFlowElement2 = bpmnDiagram.getElementByJSONID("1a1091e1-ba6b-47cd-ad7e-30787b3808cc");
        assertThat(messageFlowElement2).isInstanceOf(BPMNFlow.class);
        BPMNFlow messageFlow2 = (BPMNFlow) messageFlowElement2;
        assertThat(messageFlow2.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.MESSAGE);
        assertThat(messageFlow2.getSource()).isSameAs(followUpTask);
        assertThat(messageFlow2.getTarget()).isSameAs(applicantPool);
        assertThat(messageFlow2.getName()).isEqualTo("");

        // -------------------------------------------------------------------------------------------------------------

        // Data association

        // archiveApplicationTask - - -> dataStore
        UMLElement dataAssociationFlowElement1 = bpmnDiagram.getElementByJSONID("d3dc5376-a7f3-4bfd-83ed-429ce9e53c20");
        assertThat(dataAssociationFlowElement1).isInstanceOf(BPMNFlow.class);
        BPMNFlow dataAssociationFlow1 = (BPMNFlow) dataAssociationFlowElement1;
        assertThat(dataAssociationFlow1.getFlowType()).isEqualTo(BPMNFlow.BPMNFlowType.DATA_ASSOCIATION);
        assertThat(dataAssociationFlow1.getSource()).isSameAs(archiveApplicationTask);
        assertThat(dataAssociationFlow1.getTarget()).isSameAs(dataStore);
        assertThat(dataAssociationFlow1.getName()).isEqualTo("");

    }
}
