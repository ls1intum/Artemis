package de.tum.in.www1.artemis.service.compass.umlmodel.parsers;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.EnumUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

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
        String diagramTypeString = root.get(DIAGRAM_TYPE).getAsString();
        JsonArray modelElements = root.getAsJsonArray(ELEMENTS);
        JsonArray relationships = root.getAsJsonArray(RELATIONSHIPS);

        if (!EnumUtils.isValidEnum(DiagramType.class, diagramTypeString)) {
            throw new IllegalArgumentException("Diagram type " + diagramTypeString + " of passed JSON not supported or not recognized by JSON Parser.");
        }

        DiagramType diagramType = DiagramType.valueOf(diagramTypeString);
        return switch (diagramType) {
            case ClassDiagram -> ClassDiagramParser.buildClassDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case ActivityDiagram -> ActivityDiagramParser.buildActivityDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case UseCaseDiagram -> UseCaseDiagramParser.buildUseCaseDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case CommunicationDiagram -> CommunicationDiagramParser.buildCommunicationDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case ComponentDiagram -> ComponentDiagramParser.buildComponentDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case DeploymentDiagram -> DeploymentDiagramParser.buildDeploymentDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case ObjectDiagram -> ObjectDiagramParser.buildObjectDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case PetriNet -> PetriNetParser.buildPetriNetFromJSON(modelElements, relationships, modelSubmissionId);
            case SyntaxTree -> SyntaxTreeParser.buildSyntaxTreeFromJSON(modelElements, relationships, modelSubmissionId);
            case Flowchart -> FlowchartParser.buildFlowchartFromJSON(modelElements, relationships, modelSubmissionId);
        };
    }

    /**
     * Gets the selected element from relationship JSON object and returns the corresponding element from elements map
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param elementsMap a map containing all elements, necessary for assigning source and target element of the relationships
     * @param jsonField field to get from the relationship JSON object
     * @return the UMLElement object parsed from the JSON object
     */
    static <T extends UMLElement> T findElement(JsonObject relationshipJson, Map<String, T> elementsMap, String jsonField) {
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
}
