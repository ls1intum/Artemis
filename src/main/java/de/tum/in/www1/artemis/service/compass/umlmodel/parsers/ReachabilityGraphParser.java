package de.tum.in.www1.artemis.service.compass.umlmodel.parsers;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.*;

import java.io.IOException;
import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraph;
import de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraphArc;
import de.tum.in.www1.artemis.service.compass.umlmodel.reachabilitygraph.ReachabilityGraphMarking;

public class ReachabilityGraphParser {

    /**
     * Create a reachability graph from the model and relationship elements given as JSON arrays.
     * It parses the JSON objects to corresponding Java objects and creates a reachability graph containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a reachability graph containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static ReachabilityGraph buildReachabilityGraphFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        Map<String, ReachabilityGraphMarking> markings = new HashMap<>();
        List<ReachabilityGraphArc> arcs = new ArrayList<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();

            if (ReachabilityGraphMarking.REACHABILITY_GRAPH_MARKING_TYPE.equals(elementType)) {
                ReachabilityGraphMarking marking = parseReachabilityGraphMarking(jsonObject);
                markings.put(marking.getJSONElementID(), marking);
                allElementsMap.put(marking.getJSONElementID(), marking);
            }
        }

        // loop over all JSON control flow elements and create syntax tree links
        for (JsonElement rel : relationships) {
            Optional<ReachabilityGraphArc> reachabilityGraphArc = parseReachabilityGraphArc(rel.getAsJsonObject(), allElementsMap);
            reachabilityGraphArc.ifPresent(arcs::add);
        }

        return new ReachabilityGraph(modelSubmissionId, List.copyOf(markings.values()), arcs);
    }

    private static ReachabilityGraphMarking parseReachabilityGraphMarking(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        boolean isInitialMarking = componentJson.get("isInitialMarking").getAsBoolean();

        return new ReachabilityGraphMarking(name, componentJson.get(ELEMENT_ID).getAsString(), isInitialMarking);
    }

    private static Optional<ReachabilityGraphArc> parseReachabilityGraphArc(JsonObject relationshipJson, Map<String, UMLElement> allSyntaxTreeElements) throws IOException {
        String transition = relationshipJson.get(ELEMENT_NAME).getAsString();

        UMLElement source = UMLModelParser.findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }

        ReachabilityGraphArc newReachabilityGraphArc = new ReachabilityGraphArc(transition, source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newReachabilityGraphArc);
    }

}
