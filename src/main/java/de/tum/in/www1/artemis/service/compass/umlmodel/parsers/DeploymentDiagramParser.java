package de.tum.in.www1.artemis.service.compass.umlmodel.parsers;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.*;

import java.io.IOException;
import java.util.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponentRelationship;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.*;

public class DeploymentDiagramParser {

    /**
     * Create a UML deployment diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * deployment diagram containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML deployment diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static UMLDeploymentDiagram buildDeploymentDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        Map<String, UMLDeploymentComponent> umlComponentMap = new HashMap<>();
        Map<String, UMLDeploymentInterface> umlComponentInterfaceMap = new HashMap<>();
        Map<String, UMLNode> umlNodeMap = new HashMap<>();
        Map<String, UMLArtifact> umlArtifactMap = new HashMap<>();
        Map<String, UMLElement> allUmlElementsMap = new HashMap<>();
        List<UMLComponentRelationship> umlComponentRelationshipList = new ArrayList<>();

        // owners might not yet be available, therefore we need to store them in a map first before we can resolve them
        Map<UMLElement, String> ownerRelationships = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            UMLElement umlElement = null;
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            if (UMLDeploymentComponent.UML_COMPONENT_TYPE.equals(elementType)) {
                UMLDeploymentComponent umlComponent = parseComponent(jsonObject);
                umlComponentMap.put(umlComponent.getJSONElementID(), umlComponent);
                umlElement = umlComponent;
            }
            // NOTE: there is a difference in the json between ComponentInterface and DeploymentInterface
            else if (UMLDeploymentInterface.UML_DEPLOYMENT_INTERFACE_TYPE.equals(elementType)) {
                UMLDeploymentInterface umlDeploymentInterface = parseComponentInterface(jsonObject);
                umlComponentInterfaceMap.put(umlDeploymentInterface.getJSONElementID(), umlDeploymentInterface);
                umlElement = umlDeploymentInterface;
            }
            else if (UMLNode.UML_NODE_TYPE.equals(elementType)) {
                UMLNode umlNode = parseNode(jsonObject);
                umlNodeMap.put(umlNode.getJSONElementID(), umlNode);
                umlElement = umlNode;
            }
            else if (UMLArtifact.UML_ARTIFACT_TYPE.equals(elementType)) {
                UMLArtifact umlArtifact = parseArtifact(jsonObject);
                umlArtifactMap.put(umlArtifact.getJSONElementID(), umlArtifact);
                umlElement = umlArtifact;
            }
            if (umlElement != null) {
                allUmlElementsMap.put(umlElement.getJSONElementID(), umlElement);
                ComponentDiagramParser.findOwner(ownerRelationships, jsonObject, umlElement);
            }
        }

        // now we can resolve the owners: for this diagram type, uml components and uml nodes can be the actual owner
        ComponentDiagramParser.resolveParentComponent(allUmlElementsMap, ownerRelationships);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLComponentRelationship> componentRelationship = ComponentDiagramParser.parseComponentRelationship(rel.getAsJsonObject(), allUmlElementsMap);
            componentRelationship.ifPresent(umlComponentRelationshipList::add);
        }

        return new UMLDeploymentDiagram(modelSubmissionId, new ArrayList<>(umlComponentMap.values()), new ArrayList<>(umlComponentInterfaceMap.values()),
                umlComponentRelationshipList, new ArrayList<>(umlNodeMap.values()), new ArrayList<>(umlArtifactMap.values()));
    }

    /**
     * Parses the given JSON representation of a UML node to a UMLNode Java object.
     *
     * @param nodeJson the JSON object containing the node
     * @return the UMLNode object parsed from the JSON object
     */
    private static UMLNode parseNode(JsonObject nodeJson) {
        String nodeName = nodeJson.get(ELEMENT_NAME).getAsString();
        String stereotypeName = nodeJson.get(STEREOTYPE_NAME).getAsString();
        return new UMLNode(nodeName, stereotypeName, nodeJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML artifact to a UMLArtifact Java object.
     *
     * @param artifactJson the JSON object containing the artifact
     * @return the UMLArtifact object parsed from the JSON object
     */
    private static UMLArtifact parseArtifact(JsonObject artifactJson) {
        String artifactName = artifactJson.get(ELEMENT_NAME).getAsString();
        return new UMLArtifact(artifactName, artifactJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML deployment interface to a UMLDeploymentInterface Java object.
     *
     * @param componentInterfaceJson the JSON object containing the deployment interface
     * @return the UMLDeploymentInterface object parsed from the JSON object
     */
    protected static UMLDeploymentInterface parseComponentInterface(JsonObject componentInterfaceJson) {
        String componentInterfaceName = componentInterfaceJson.get(ELEMENT_NAME).getAsString();
        return new UMLDeploymentInterface(componentInterfaceName, componentInterfaceJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML component to a UMLDeploymentComponent Java object.
     *
     * @param componentJson the JSON object containing the component
     * @return the UMLDeploymentComponent object parsed from the JSON object
     */
    protected static UMLDeploymentComponent parseComponent(JsonObject componentJson) {
        String componentName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLDeploymentComponent(componentName, componentJson.get(ELEMENT_ID).getAsString());
    }

}
