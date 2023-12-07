package de.tum.in.www1.artemis.service.compass.umlmodel.parsers.v3;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLContainerElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.bpmn.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.parsers.UMLModelParser;

public class BPMNDiagramParser {

    /**
     * Create a BPMN diagram from the model and control flow elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates an activity
     * diagram containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the control flow elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML activity diagram containing the parsed model elements and control flows
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the control flow JSON objects
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

        // loop over all JSON elements and create activity and activity node objects
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
            ;
        }

        // loop over all JSON elements again to connect parent activity elements with their child elements
        for (var entry : modelElements.entrySet()) {
            JsonObject jsonObject = entry.getValue().getAsJsonObject();

            if (jsonObject.has(ELEMENT_OWNER) && !jsonObject.get(ELEMENT_OWNER).isJsonNull()) {
                String parentId = jsonObject.get(ELEMENT_OWNER).getAsString();
                UMLElement parentElement = elementMap.get(parentId);
                UMLElement childElement = elementMap.get(jsonObject.get(ELEMENT_ID).getAsString());

                if (parentElement instanceof UMLContainerElement) {
                    UMLContainerElement parentContainer = (UMLContainerElement) parentElement;
                    parentContainer.addSubElement(childElement);
                }
            }
        }

        // loop over all JSON control flow elements and create control flow objects
        for (var entry : relationships.entrySet()) {
            JsonObject jsonObject = entry.getValue().getAsJsonObject();
            BPMNFlow flow = parseFlow(jsonObject, elementMap);
            flows.add(flow);
        }

        return new BPMNDiagram(modelSubmissionId, annotations, callActivities, dataObjects, dataStores, endEvents, gateways, groups, intermediateEvents, pools, startEvents,
                subprocesses, swimlanes, tasks, transactions, flows);
    }

    /**
     *
     * @param annotationJson
     * @return
     */
    private static BPMNAnnotation parseAnnotation(JsonObject annotationJson) {
        String jsonElementId = annotationJson.get(ELEMENT_ID).getAsString();
        String name = annotationJson.get(ELEMENT_NAME).getAsString();
        return new BPMNAnnotation(name, jsonElementId);
    }

    /**
     *
     * @param callActivityJson
     * @return
     */
    private static BPMNCallActivity parseCallActivity(JsonObject callActivityJson) {
        String jsonElementId = callActivityJson.get(ELEMENT_ID).getAsString();
        String name = callActivityJson.get(ELEMENT_NAME).getAsString();
        return new BPMNCallActivity(name, jsonElementId);
    }

    /**
     *
     * @param dataObjectJson
     * @return
     */
    private static BPMNDataObject parseDataObject(JsonObject dataObjectJson) {
        String jsonElementId = dataObjectJson.get(ELEMENT_ID).getAsString();
        String name = dataObjectJson.get(ELEMENT_NAME).getAsString();
        return new BPMNDataObject(name, jsonElementId);
    }

    private static BPMNDataStore parseDataStore(JsonObject dataStoreJson) {
        String jsonElementId = dataStoreJson.get(ELEMENT_ID).getAsString();
        String name = dataStoreJson.get(ELEMENT_NAME).getAsString();
        return new BPMNDataStore(name, jsonElementId);
    }

    private static BPMNEndEvent parseEndEvent(JsonObject endEventJson) {
        String jsonElementId = endEventJson.get(ELEMENT_ID).getAsString();
        String name = endEventJson.get(ELEMENT_NAME).getAsString();
        return new BPMNEndEvent(name, jsonElementId);
    }

    private static BPMNGateway parseGateway(JsonObject gatewayJson) {
        String jsonElementId = gatewayJson.get(ELEMENT_ID).getAsString();
        String name = gatewayJson.get(ELEMENT_NAME).getAsString();
        return new BPMNGateway(name, jsonElementId);
    }

    private static BPMNGroup parseGroup(JsonObject groupJson) {
        String jsonElementId = groupJson.get(ELEMENT_ID).getAsString();
        String name = groupJson.get(ELEMENT_NAME).getAsString();
        return new BPMNGroup(name, jsonElementId);
    }

    private static BPMNIntermediateEvent parseIntermediateEvent(JsonObject intermediateEventJson) {
        String jsonElementId = intermediateEventJson.get(ELEMENT_ID).getAsString();
        String name = intermediateEventJson.get(ELEMENT_NAME).getAsString();
        return new BPMNIntermediateEvent(name, jsonElementId);
    }

    private static BPMNPool parsePool(JsonObject poolJson) {
        String jsonElementId = poolJson.get(ELEMENT_ID).getAsString();
        String name = poolJson.get(ELEMENT_NAME).getAsString();
        return new BPMNPool(name, jsonElementId);
    }

    private static BPMNStartEvent parseStartEvent(JsonObject startEventJson) {
        String jsonElementId = startEventJson.get(ELEMENT_ID).getAsString();
        String name = startEventJson.get(ELEMENT_NAME).getAsString();
        return new BPMNStartEvent(name, jsonElementId);
    }

    private static BPMNSubprocess parseSubprocess(JsonObject subprocessJson) {
        String jsonElementId = subprocessJson.get(ELEMENT_ID).getAsString();
        String name = subprocessJson.get(ELEMENT_NAME).getAsString();
        return new BPMNSubprocess(name, jsonElementId);
    }

    private static BPMNSwimlane parseSwimlane(JsonObject swimlaneJson) {
        String jsonElementId = swimlaneJson.get(ELEMENT_ID).getAsString();
        String name = swimlaneJson.get(ELEMENT_NAME).getAsString();
        return new BPMNSwimlane(name, jsonElementId);
    }

    private static BPMNTask parseTask(JsonObject taskJson) {
        String jsonElementId = taskJson.get(ELEMENT_ID).getAsString();
        String name = taskJson.get(ELEMENT_NAME).getAsString();
        return new BPMNTask(name, jsonElementId);
    }

    private static BPMNTransaction parseTransaction(JsonObject transactionJson) {
        String jsonElementId = transactionJson.get(ELEMENT_ID).getAsString();
        String name = transactionJson.get(ELEMENT_NAME).getAsString();
        return new BPMNTransaction(name, jsonElementId);
    }

    /**
     *
     * @param flowJson
     * @param activityElementMap
     * @return
     * @throws IOException
     */
    private static BPMNFlow parseFlow(JsonObject flowJson, Map<String, UMLElement> activityElementMap) throws IOException {
        UMLElement source = UMLModelParser.findElement(flowJson, activityElementMap, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(flowJson, activityElementMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            return new BPMNFlow(source, target, flowJson.get(ELEMENT_ID).getAsString());
        }
        else {
            throw new IOException("Flow source " + source + " or target " + target + " not part of model!");
        }
    }

}
