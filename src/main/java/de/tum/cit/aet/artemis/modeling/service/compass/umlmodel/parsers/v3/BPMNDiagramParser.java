package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.v3;

import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_OWNER;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLContainerElement;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNAnnotation;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNCallActivity;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNDataObject;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNDataStore;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNDiagram;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNEndEvent;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNFlow;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNGateway;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNGroup;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNIntermediateEvent;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNPool;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNStartEvent;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNSubprocess;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNSwimlane;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNTask;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.bpmn.BPMNTransaction;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.UMLModelParser;

public class BPMNDiagramParser {

    /**
     * Build a BPMN diagram object from a BPMN diagram JSON
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the flow elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a BPMN diagram containing the parsed model elements and flows
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the flow JSON objects
     */
    protected static BPMNDiagram buildBPMNDiagramFromJSON(JsonObject modelElements, JsonObject relationships, long modelSubmissionId) throws IOException {
        Map<String, UMLElement> elementMap = new HashMap<>();

        List<BPMNAnnotation> annotations = new ArrayList<>();
        List<BPMNCallActivity> callActivities = new ArrayList<>();
        List<BPMNDataObject> dataObjects = new ArrayList<>();
        List<BPMNDataStore> dataStores = new ArrayList<>();
        List<BPMNEndEvent> endEvents = new ArrayList<>();
        List<BPMNGateway> gateways = new ArrayList<>();
        List<BPMNGroup> groups = new ArrayList<>();
        List<BPMNIntermediateEvent> intermediateEvents = new ArrayList<>();
        List<BPMNPool> pools = new ArrayList<>();
        List<BPMNStartEvent> startEvents = new ArrayList<>();
        List<BPMNSubprocess> subprocesses = new ArrayList<>();
        List<BPMNSwimlane> swimlanes = new ArrayList<>();
        List<BPMNTask> tasks = new ArrayList<>();
        List<BPMNTransaction> transactions = new ArrayList<>();
        List<BPMNFlow> flows = new ArrayList<>();

        // Parse object representations for all JSON model elements
        for (var entry : modelElements.entrySet()) {
            JsonObject jsonObject = entry.getValue().getAsJsonObject();

            String objectType = jsonObject.get(ELEMENT_TYPE).getAsString();

            switch (objectType) {
                case "BPMNAnnotation":
                    BPMNAnnotation annotation = parseAnnotation(jsonObject);
                    annotations.add(annotation);
                    elementMap.put(annotation.getJSONElementID(), annotation);
                    break;
                case "BPMNCallActivity":
                    BPMNCallActivity callActivity = parseCallActivity(jsonObject);
                    callActivities.add(callActivity);
                    elementMap.put(callActivity.getJSONElementID(), callActivity);
                    break;
                case "BPMNDataObject":
                    BPMNDataObject dataObject = parseDataObject(jsonObject);
                    dataObjects.add(dataObject);
                    elementMap.put(dataObject.getJSONElementID(), dataObject);
                    break;
                case "BPMNDataStore":
                    BPMNDataStore dataStore = parseDataStore(jsonObject);
                    dataStores.add(dataStore);
                    elementMap.put(dataStore.getJSONElementID(), dataStore);
                    break;
                case "BPMNEndEvent":
                    BPMNEndEvent endEvent = parseEndEvent(jsonObject);
                    endEvents.add(endEvent);
                    elementMap.put(endEvent.getJSONElementID(), endEvent);
                    break;
                case "BPMNGateway":
                    BPMNGateway gateway = parseGateway(jsonObject);
                    gateways.add(gateway);
                    elementMap.put(gateway.getJSONElementID(), gateway);
                    break;
                case "BPMNGroup":
                    BPMNGroup group = parseGroup(jsonObject);
                    groups.add(group);
                    elementMap.put(group.getJSONElementID(), group);
                    break;
                case "BPMNIntermediateEvent":
                    BPMNIntermediateEvent intermediateEvent = parseIntermediateEvent(jsonObject);
                    intermediateEvents.add(intermediateEvent);
                    elementMap.put(intermediateEvent.getJSONElementID(), intermediateEvent);
                    break;
                case "BPMNPool":
                    BPMNPool pool = parsePool(jsonObject);
                    pools.add(pool);
                    elementMap.put(pool.getJSONElementID(), pool);
                    break;
                case "BPMNStartEvent":
                    BPMNStartEvent startEvent = parseStartEvent(jsonObject);
                    startEvents.add(startEvent);
                    elementMap.put(startEvent.getJSONElementID(), startEvent);
                    break;
                case "BPMNSubprocess":
                    BPMNSubprocess subprocess = parseSubprocess(jsonObject);
                    subprocesses.add(subprocess);
                    elementMap.put(subprocess.getJSONElementID(), subprocess);
                    break;
                case "BPMNSwimlane":
                    BPMNSwimlane swimlane = parseSwimlane(jsonObject);
                    swimlanes.add(swimlane);
                    elementMap.put(swimlane.getJSONElementID(), swimlane);
                    break;
                case "BPMNTask":
                    BPMNTask task = parseTask(jsonObject);
                    tasks.add(task);
                    elementMap.put(task.getJSONElementID(), task);
                    break;
                case "BPMNTransaction":
                    BPMNTransaction transaction = parseTransaction(jsonObject);
                    transactions.add(transaction);
                    elementMap.put(transaction.getJSONElementID(), transaction);
                    break;
                default:
                    break;
            }
        }

        // Loop over the model elements again to assign their respective parent elements
        for (var entry : modelElements.entrySet()) {
            JsonObject jsonObject = entry.getValue().getAsJsonObject();

            if (jsonObject.has(ELEMENT_OWNER) && !jsonObject.get(ELEMENT_OWNER).isJsonNull()) {
                String parentId = jsonObject.get(ELEMENT_OWNER).getAsString();
                UMLElement parentElement = elementMap.get(parentId);
                UMLElement childElement = elementMap.get(jsonObject.get(ELEMENT_ID).getAsString());

                if (parentElement instanceof UMLContainerElement parentContainer) {
                    parentContainer.addSubElement(childElement);
                    childElement.setParentElement(parentContainer);
                }
            }
        }

        // Parse flows between model elements and assign source and target respectively
        for (var entry : relationships.entrySet()) {
            JsonObject jsonObject = entry.getValue().getAsJsonObject();
            BPMNFlow flow = parseFlow(jsonObject, elementMap);
            flows.add(flow);
        }

        return new BPMNDiagram(modelSubmissionId, annotations, callActivities, dataObjects, dataStores, endEvents, gateways, groups, intermediateEvents, pools, startEvents,
                subprocesses, swimlanes, tasks, transactions, flows);
    }

    /**
     * Parse a BPMNAnnotation from a JsonObject
     *
     * @param annotationJson The JsonObject to parse
     * @return The parsed BPMNAnnotation
     */
    private static BPMNAnnotation parseAnnotation(JsonObject annotationJson) {
        String jsonElementId = annotationJson.get(ELEMENT_ID).getAsString();
        String name = annotationJson.get(ELEMENT_NAME).getAsString();
        return new BPMNAnnotation(name, jsonElementId);
    }

    /**
     * Parse a BPMNCallActivity from a JsonObject
     *
     * @param callActivityJson The JsonObject to parse
     * @return The parsed BPMNCallActivity
     */
    private static BPMNCallActivity parseCallActivity(JsonObject callActivityJson) {
        String jsonElementId = callActivityJson.get(ELEMENT_ID).getAsString();
        String name = callActivityJson.get(ELEMENT_NAME).getAsString();
        return new BPMNCallActivity(name, jsonElementId);
    }

    /**
     * Parse a BPMNDataObject from a JsonObject
     *
     * @param dataObjectJson The JsonObject to parse
     * @return The parsed BPMNDataObject
     */
    private static BPMNDataObject parseDataObject(JsonObject dataObjectJson) {
        String jsonElementId = dataObjectJson.get(ELEMENT_ID).getAsString();
        String name = dataObjectJson.get(ELEMENT_NAME).getAsString();
        return new BPMNDataObject(name, jsonElementId);
    }

    /**
     * Parse a BPMNDataStore from a JsonObject
     *
     * @param dataStoreJson The JsonObject to parse
     * @return The parsed BPMNDataStore
     */
    private static BPMNDataStore parseDataStore(JsonObject dataStoreJson) {
        String jsonElementId = dataStoreJson.get(ELEMENT_ID).getAsString();
        String name = dataStoreJson.get(ELEMENT_NAME).getAsString();
        return new BPMNDataStore(name, jsonElementId);
    }

    /**
     * Parse a BPMNEndEvent from a JsonObject
     *
     * @param endEventJson The JsonObject to parse
     * @return The parsed BPMNEndEvent
     */
    private static BPMNEndEvent parseEndEvent(JsonObject endEventJson) {
        String jsonElementId = endEventJson.get(ELEMENT_ID).getAsString();
        String name = endEventJson.get(ELEMENT_NAME).getAsString();

        BPMNEndEvent.BPMNEndEventType eventType = BPMNEndEvent.BPMNEndEventType.fromValue(endEventJson.get("eventType").getAsString())
                .orElse(BPMNEndEvent.BPMNEndEventType.DEFAULT);

        return new BPMNEndEvent(name, jsonElementId, eventType);
    }

    /**
     * Parse a BPMNGateway from a JsonObject
     *
     * @param gatewayJson The JsonObject to parse
     * @return The parsed BPMNGateway
     */
    private static BPMNGateway parseGateway(JsonObject gatewayJson) {
        String jsonElementId = gatewayJson.get(ELEMENT_ID).getAsString();
        String name = gatewayJson.get(ELEMENT_NAME).getAsString();

        BPMNGateway.BPMNGatewayType gatewayType = BPMNGateway.BPMNGatewayType.fromValue(gatewayJson.get("gatewayType").getAsString()).orElse(BPMNGateway.BPMNGatewayType.EXCLUSIVE);

        return new BPMNGateway(name, jsonElementId, gatewayType);
    }

    /**
     * Parse a BPMNGroup from a JsonObject
     *
     * @param groupJson The JsonObject to parse
     * @return The parsed BPMNGroup
     */
    private static BPMNGroup parseGroup(JsonObject groupJson) {
        String jsonElementId = groupJson.get(ELEMENT_ID).getAsString();
        String name = groupJson.get(ELEMENT_NAME).getAsString();
        return new BPMNGroup(name, jsonElementId);
    }

    /**
     * Parse a BPMNIntermediateEvent from a JsonObject
     *
     * @param intermediateEventJson The JsonObject to parse
     * @return The parsed BPMNIntermediateEvent
     */
    private static BPMNIntermediateEvent parseIntermediateEvent(JsonObject intermediateEventJson) {
        String jsonElementId = intermediateEventJson.get(ELEMENT_ID).getAsString();
        String name = intermediateEventJson.get(ELEMENT_NAME).getAsString();

        BPMNIntermediateEvent.BPMNIntermediateEventType eventType = BPMNIntermediateEvent.BPMNIntermediateEventType.fromValue(intermediateEventJson.get("eventType").getAsString())
                .orElse(BPMNIntermediateEvent.BPMNIntermediateEventType.DEFAULT);

        return new BPMNIntermediateEvent(name, jsonElementId, eventType);
    }

    /**
     * Parse a BPMNPool from a JsonObject
     *
     * @param poolJson The JsonObject to parse
     * @return The parsed BPMNPool
     */
    private static BPMNPool parsePool(JsonObject poolJson) {
        String jsonElementId = poolJson.get(ELEMENT_ID).getAsString();
        String name = poolJson.get(ELEMENT_NAME).getAsString();
        return new BPMNPool(name, jsonElementId);
    }

    /**
     * Parse a BPMNStartEvent from a JsonObject
     *
     * @param startEventJson The JsonObject to parse
     * @return The parsed BPMNStartEvent
     */
    private static BPMNStartEvent parseStartEvent(JsonObject startEventJson) {
        String jsonElementId = startEventJson.get(ELEMENT_ID).getAsString();
        String name = startEventJson.get(ELEMENT_NAME).getAsString();

        BPMNStartEvent.BPMNStartEventType eventType = BPMNStartEvent.BPMNStartEventType.fromValue(startEventJson.get("eventType").getAsString())
                .orElse(BPMNStartEvent.BPMNStartEventType.DEFAULT);

        return new BPMNStartEvent(name, jsonElementId, eventType);
    }

    /**
     * Parse a BPMNSubprocess from a JsonObject
     *
     * @param subprocessJson The JsonObject to parse
     * @return The parsed BPMNSubprocess
     */
    private static BPMNSubprocess parseSubprocess(JsonObject subprocessJson) {
        String jsonElementId = subprocessJson.get(ELEMENT_ID).getAsString();
        String name = subprocessJson.get(ELEMENT_NAME).getAsString();
        return new BPMNSubprocess(name, jsonElementId);
    }

    /**
     * Parse a BPMNSwimlane from a given JsonObject
     *
     * @param swimlaneJson The JsonObjectt to parse
     * @return The parsed BPMNSwimlane
     */
    private static BPMNSwimlane parseSwimlane(JsonObject swimlaneJson) {
        String jsonElementId = swimlaneJson.get(ELEMENT_ID).getAsString();
        String name = swimlaneJson.get(ELEMENT_NAME).getAsString();
        return new BPMNSwimlane(name, jsonElementId);
    }

    /**
     * Parse a BPMNTask from a given JsonObject
     *
     * @param taskJson The JsonObject to parse
     * @return The parsed BPMNTask
     */
    private static BPMNTask parseTask(JsonObject taskJson) {
        String jsonElementId = taskJson.get(ELEMENT_ID).getAsString();
        String name = taskJson.get(ELEMENT_NAME).getAsString();

        BPMNTask.BPMNTaskType taskType = BPMNTask.BPMNTaskType.fromValue(taskJson.get("taskType").getAsString()).orElse(BPMNTask.BPMNTaskType.DEFAULT);

        BPMNTask.BPMNMarker marker = BPMNTask.BPMNMarker.fromValue(taskJson.get("marker").getAsString()).orElse(BPMNTask.BPMNMarker.NONE);

        return new BPMNTask(name, jsonElementId, taskType, marker);
    }

    /**
     * Parse a BPMNTransaction from a given JsonObject
     *
     * @param transactionJson The JsonObject to parse
     * @return The parsed BPMNTransaction
     */
    private static BPMNTransaction parseTransaction(JsonObject transactionJson) {
        String jsonElementId = transactionJson.get(ELEMENT_ID).getAsString();
        String name = transactionJson.get(ELEMENT_NAME).getAsString();
        return new BPMNTransaction(name, jsonElementId);
    }

    /**
     * Parse a BPMNFlow from a given JsonObject
     *
     * @param flowJson   The JsonObject to parse
     * @param elementMap A map containing all other diagram elements indexed by their ID
     * @return The parsed BPMNFlow
     * @throws IOException Thrown if either the source or the target elements are not found in the element map
     */
    private static BPMNFlow parseFlow(JsonObject flowJson, Map<String, UMLElement> elementMap) throws IOException {

        String jsonElementId = flowJson.get(ELEMENT_ID).getAsString();
        String name = flowJson.get(ELEMENT_NAME).getAsString();
        UMLElement source = UMLModelParser.findElement(flowJson, elementMap, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(flowJson, elementMap, RELATIONSHIP_TARGET);

        BPMNFlow.BPMNFlowType flowType = BPMNFlow.BPMNFlowType.fromValue(flowJson.get("flowType").getAsString()).orElse(BPMNFlow.BPMNFlowType.SEQUENCE);

        if (source != null && target != null) {
            return new BPMNFlow(name, jsonElementId, flowType, source, target);
        }
        else {
            throw new IOException("Flow source " + source + " or target " + target + " not part of model!");
        }
    }
}
