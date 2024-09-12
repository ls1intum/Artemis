package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.DIAGRAM_TYPE;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENTS;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIPS;

import java.io.IOException;

import org.apache.commons.lang3.EnumUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.UMLDiagram;

public class UMLModelV2Parser {

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
            case BPMN -> throw new IllegalArgumentException("The V2 schema is not supported for BPMN diagrams");
        };
    }
}
