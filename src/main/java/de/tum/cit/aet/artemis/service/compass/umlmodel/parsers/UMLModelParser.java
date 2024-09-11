package de.tum.cit.aet.artemis.service.compass.umlmodel.parsers;

import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.DIAGRAM_VERSION;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_ENDPOINT_ID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.v2.UMLModelV2Parser;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.v3.UMLModelV3Parser;

public class UMLModelParser {

    /**
     * Create a UML diagram from a given JSON object.
     *
     * @param root              the JSON object containing the JSON representation of a UML diagram
     * @param modelSubmissionId the ID of the modeling submission containing the given UML diagram
     * @return the UML diagram as Java object
     * @throws IOException on unexpected JSON formats
     */
    public static UMLDiagram buildModelFromJSON(JsonObject root, long modelSubmissionId) throws IOException {
        String diagramVersion = root.get(DIAGRAM_VERSION).getAsString();

        if (diagramVersion.startsWith("2.")) {
            return UMLModelV2Parser.buildModelFromJSON(root, modelSubmissionId);
        }
        else if (diagramVersion.startsWith("3.")) {
            return UMLModelV3Parser.buildModelFromJSON(root, modelSubmissionId);
        }
        else {
            throw new IllegalArgumentException("Diagram version " + diagramVersion + " of passed JSON not supported or not recognized by JSON Parser.");
        }
    }

    /**
     * Gets the selected element from relationship JSON object and returns the corresponding element from elements map
     *
     * @param <T>              the type of UML elements to look for (inferred from `elementsMap`)
     * @param relationshipJson the JSON object containing the relationship
     * @param elementsMap      a map containing all elements, necessary for assigning source and target element of the relationships
     * @param jsonField        field to get from the relationship JSON object
     * @return the UMLElement object parsed from the JSON object
     */
    public static <T extends UMLElement> T findElement(JsonObject relationshipJson, Map<String, T> elementsMap, String jsonField) {
        JsonObject relationshipSource = relationshipJson.getAsJsonObject(jsonField);
        String sourceJSONID = relationshipSource.get(RELATIONSHIP_ENDPOINT_ID).getAsString();
        return elementsMap.get(sourceJSONID);
    }

    /**
     * Create a map from the elements of the given JSON array. Every entry contains the ID of the element as key and the corresponding JSON element as value.
     *
     * @param elements a JSON array of elements from which the map should be created
     * @return a map that maps elementId -> element
     */
    public static Map<String, JsonObject> generateJsonElementMap(JsonArray elements) {
        Map<String, JsonObject> jsonElementMap = new HashMap<>();
        elements.forEach(element -> jsonElementMap.put(element.getAsJsonObject().get("id").getAsString(), element.getAsJsonObject()));
        return jsonElementMap;
    }

    /**
     * Create a map from the elements of the given JSON object. Converts each element to a JSON object.
     *
     * @param elements a JSON object of elements from which the map should be created
     * @return a map that maps elementId -> element
     */
    public static Map<String, JsonObject> generateJsonElementMap(JsonObject elements) {
        Map<String, JsonObject> jsonElementMap = new HashMap<>();
        for (var entry : elements.entrySet()) {
            jsonElementMap.put(entry.getKey(), entry.getValue().getAsJsonObject());
        }

        return jsonElementMap;
    }
}
