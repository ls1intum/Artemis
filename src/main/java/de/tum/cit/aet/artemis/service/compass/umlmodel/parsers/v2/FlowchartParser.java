package de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.Flowchart;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.FlowchartDecision;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.FlowchartFlowline;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.FlowchartFunctionCall;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.FlowchartInputOutput;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.FlowchartProcess;
import de.tum.cit.aet.artemis.service.compass.umlmodel.flowchart.FlowchartTerminal;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.UMLModelParser;

public class FlowchartParser {

    /**
     * Create a flowchart from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * flowchart containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a flowchart containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static Flowchart buildFlowchartFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<FlowchartFlowline> flowchartFlowlineList = new ArrayList<>();
        Map<String, FlowchartTerminal> terminalMap = new HashMap<>();
        Map<String, FlowchartDecision> decisionMap = new HashMap<>();
        Map<String, FlowchartProcess> processMap = new HashMap<>();
        Map<String, FlowchartInputOutput> inputOutputMap = new HashMap<>();
        Map<String, FlowchartFunctionCall> functionCallMap = new HashMap<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            // elementType is never null
            switch (elementType) {
                case FlowchartTerminal.FLOWCHART_TERMINAL_TYPE -> {
                    FlowchartTerminal terminal = parseFlowchartTerminal(jsonObject);
                    terminalMap.put(terminal.getJSONElementID(), terminal);
                    allElementsMap.put(terminal.getJSONElementID(), terminal);
                }
                case FlowchartDecision.FLOWCHART_DECISION_TYPE -> {
                    FlowchartDecision decision = parseFlowchartDecision(jsonObject);
                    decisionMap.put(decision.getJSONElementID(), decision);
                    allElementsMap.put(decision.getJSONElementID(), decision);
                }
                case FlowchartProcess.FLOWCHART_PROCESS_TYPE -> {
                    FlowchartProcess process = parseFlowchartProcess(jsonObject);
                    processMap.put(process.getJSONElementID(), process);
                    allElementsMap.put(process.getJSONElementID(), process);
                }
                case FlowchartInputOutput.FLOWCHART_INPUT_OUTPUT_TYPE -> {
                    FlowchartInputOutput inputOutput = parseFlowchartInputOutput(jsonObject);
                    inputOutputMap.put(inputOutput.getJSONElementID(), inputOutput);
                    allElementsMap.put(inputOutput.getJSONElementID(), inputOutput);
                }
                case FlowchartFunctionCall.FLOWCHART_FUNCTION_CALL_TYPE -> {
                    FlowchartFunctionCall functionCall = parseFlowchartFunctionCall(jsonObject);
                    functionCallMap.put(functionCall.getJSONElementID(), functionCall);
                    allElementsMap.put(functionCall.getJSONElementID(), functionCall);
                }
                default -> {
                    // ignore unknown elements
                }
            }
        }

        // loop over all JSON flowchart elements and create flowlines
        for (JsonElement rel : relationships) {
            Optional<FlowchartFlowline> flowchartFlowline = parseFlowchartFlowline(rel.getAsJsonObject(), allElementsMap);
            flowchartFlowline.ifPresent(flowchartFlowlineList::add);
        }

        return new Flowchart(modelSubmissionId, List.copyOf(terminalMap.values()), List.copyOf(processMap.values()), List.copyOf(decisionMap.values()),
                List.copyOf(inputOutputMap.values()), List.copyOf(functionCallMap.values()), flowchartFlowlineList);
    }

    /**
     * Parses the given JSON representation of a Flowchart Terminal to a FlowchartTerminal Java object.
     *
     * @param componentJson the JSON object containing the terminal
     * @return the FlowchartTerminal object parsed from the JSON object
     */
    private static FlowchartTerminal parseFlowchartTerminal(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartTerminal(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Process to a FlowchartProcess Java object.
     *
     * @param componentJson the JSON object containing the process
     * @return the FlowchartProcess object parsed from the JSON object
     */
    private static FlowchartProcess parseFlowchartProcess(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartProcess(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Decision to a FlowchartDecision Java object.
     *
     * @param componentJson the JSON object containing the decision
     * @return the FlowchartDecision object parsed from the JSON object
     */
    private static FlowchartDecision parseFlowchartDecision(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartDecision(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Function Call to a FlowchartFunctionCall Java object.
     *
     * @param componentJson the JSON object containing the function call
     * @return the FlowchartFunctionCall object parsed from the JSON object
     */
    private static FlowchartFunctionCall parseFlowchartFunctionCall(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartFunctionCall(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Input Output to a FlowchartInputOutput Java object.
     *
     * @param componentJson the JSON object containing the input output
     * @return the FlowchartInputOutput object parsed from the JSON object
     */
    private static FlowchartInputOutput parseFlowchartInputOutput(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartInputOutput(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a FlowchartFlowline Java object.
     *
     * @param relationshipJson     the JSON object containing the relationship
     * @param allFlowchartElements a map containing all objects of the corresponding flowchart, necessary for assigning source and target element of the relationships
     * @return the FlowchartFlowline object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<FlowchartFlowline> parseFlowchartFlowline(JsonObject relationshipJson, Map<String, UMLElement> allFlowchartElements) throws IOException {
        UMLElement source = UMLModelParser.findElement(relationshipJson, allFlowchartElements, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(relationshipJson, allFlowchartElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }
        FlowchartFlowline newFlowchartFlowline = new FlowchartFlowline(source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newFlowchartFlowline);
    }

}
