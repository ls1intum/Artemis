package de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_OWNER;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.EnumUtils;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.component.UMLComponent;
import de.tum.cit.aet.artemis.service.compass.umlmodel.component.UMLComponentDiagram;
import de.tum.cit.aet.artemis.service.compass.umlmodel.component.UMLComponentInterface;
import de.tum.cit.aet.artemis.service.compass.umlmodel.component.UMLComponentRelationship;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.UMLModelParser;

public class ComponentDiagramParser {

    /**
     * Create a UML component diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * component diagram containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML component diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static UMLComponentDiagram buildComponentDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        Map<String, UMLComponent> umlComponentMap = new HashMap<>();
        Map<String, UMLComponentInterface> umlComponentInterfaceMap = new HashMap<>();
        Map<String, UMLElement> allUmlElementsMap = new HashMap<>();
        List<UMLComponentRelationship> umlComponentRelationshipList = new ArrayList<>();

        // owners might not yet be available, therefore we need to store them in a map first before we can resolve them
        Map<UMLElement, String> ownerRelationships = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            UMLElement umlElement = null;
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            if (UMLComponent.UML_COMPONENT_TYPE.equals(elementType)) {
                UMLComponent umlComponent = parseComponent(jsonObject);
                umlComponentMap.put(umlComponent.getJSONElementID(), umlComponent);
                umlElement = umlComponent;
            }
            else if (UMLComponentInterface.UML_COMPONENT_INTERFACE_TYPE.equals(elementType)) {
                UMLComponentInterface umlComponentInterface = parseComponentInterface(jsonObject);
                umlComponentInterfaceMap.put(umlComponentInterface.getJSONElementID(), umlComponentInterface);
                umlElement = umlComponentInterface;
            }
            if (umlElement != null) {
                allUmlElementsMap.put(umlElement.getJSONElementID(), umlElement);
                findOwner(ownerRelationships, jsonObject, umlElement);
            }
        }

        // now we can resolve the owners: for this diagram type, only uml components can be the actual owner
        resolveParentComponent(allUmlElementsMap, ownerRelationships);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLComponentRelationship> componentRelationship = parseComponentRelationship(rel.getAsJsonObject(), allUmlElementsMap);
            componentRelationship.ifPresent(umlComponentRelationshipList::add);
        }

        return new UMLComponentDiagram(modelSubmissionId, new ArrayList<>(umlComponentMap.values()), new ArrayList<>(umlComponentInterfaceMap.values()),
                umlComponentRelationshipList);
    }

    /**
     * Parses the given JSON representation of a UML component interface to a UMLComponentInterface Java object.
     *
     * @param relationshipJson  the JSON object containing the component interface
     * @param allUmlElementsMap the JSON object containing the component interface
     * @return the UMLComponentInterface object parsed from the JSON object
     */
    protected static Optional<UMLComponentRelationship> parseComponentRelationship(JsonObject relationshipJson, Map<String, UMLElement> allUmlElementsMap) throws IOException {

        String relationshipType = relationshipJson.get(RELATIONSHIP_TYPE).getAsString();
        relationshipType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, relationshipType);

        if (!EnumUtils.isValidEnum(UMLComponentRelationship.UMLComponentRelationshipType.class, relationshipType)) {
            return Optional.empty();
        }

        UMLElement source = UMLModelParser.findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            UMLComponentRelationship newComponentRelationship = new UMLComponentRelationship(source, target,
                    UMLComponentRelationship.UMLComponentRelationshipType.valueOf(relationshipType), relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newComponentRelationship);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

    /**
     * Parses the given JSON representation of a UML component interface to a UMLComponentInterface Java object.
     *
     * @param componentInterfaceJson the JSON object containing the component interface
     * @return the UMLComponentInterface object parsed from the JSON object
     */
    protected static UMLComponentInterface parseComponentInterface(JsonObject componentInterfaceJson) {
        String componentInterfaceName = componentInterfaceJson.get(ELEMENT_NAME).getAsString();
        return new UMLComponentInterface(componentInterfaceName, componentInterfaceJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML component to a UMLComponent Java object.
     *
     * @param componentJson the JSON object containing the component
     * @return the UMLComponent object parsed from the JSON object
     */
    protected static UMLComponent parseComponent(JsonObject componentJson) {
        String componentName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLComponent(componentName, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Finds the owner element of relationship and sets parent of relationship to that element
     *
     * @param allUmlElementsMap  map of uml elements and ids to find owner element
     * @param ownerRelationships map of uml elements and ids of their owners
     */
    protected static void resolveParentComponent(Map<String, UMLElement> allUmlElementsMap, Map<UMLElement, String> ownerRelationships) {
        for (var ownerEntry : ownerRelationships.entrySet()) {
            String ownerId = ownerEntry.getValue();
            UMLElement umlElement = ownerEntry.getKey();
            UMLElement parentComponent = allUmlElementsMap.get(ownerId);
            umlElement.setParentElement(parentComponent);
        }
    }

    /**
     * Gets the owner id from element's json object and puts it into a relationship map
     *
     * @param ownerRelationships map of uml relationship elements and their ids
     * @param jsonObject         json representation of element
     * @param umlElement         uml element
     */
    protected static void findOwner(Map<UMLElement, String> ownerRelationships, JsonObject jsonObject, UMLElement umlElement) {
        if (jsonObject.has(ELEMENT_OWNER) && !jsonObject.get(ELEMENT_OWNER).isJsonNull()) {
            String ownerId = jsonObject.get(ELEMENT_OWNER).getAsString();
            ownerRelationships.put(umlElement, ownerId);
        }
    }

}
