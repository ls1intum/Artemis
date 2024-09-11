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
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.UMLModelParser;
import de.tum.cit.aet.artemis.service.compass.umlmodel.usecase.UMLActor;
import de.tum.cit.aet.artemis.service.compass.umlmodel.usecase.UMLSystemBoundary;
import de.tum.cit.aet.artemis.service.compass.umlmodel.usecase.UMLUseCase;
import de.tum.cit.aet.artemis.service.compass.umlmodel.usecase.UMLUseCaseAssociation;
import de.tum.cit.aet.artemis.service.compass.umlmodel.usecase.UMLUseCaseDiagram;

public class UseCaseDiagramParser {

    /**
     * Create a UML use case diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * use case diagram containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML use case diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static UMLUseCaseDiagram buildUseCaseDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        Map<String, UMLSystemBoundary> umlSystemBoundaryMap = new HashMap<>();
        Map<String, UMLActor> umlActorMap = new HashMap<>();
        Map<String, UMLUseCase> umlUseCaseMap = new HashMap<>();
        Map<String, UMLElement> allUmlElementsMap = new HashMap<>();
        List<UMLUseCaseAssociation> umlUseCaseAssociationList = new ArrayList<>();

        // owners might not yet be available, therefore we need to store them in a map first before we can resolve them
        Map<UMLElement, String> ownerRelationships = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            UMLElement umlElement = null;
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            if (UMLSystemBoundary.UML_SYSTEM_BOUNDARY_TYPE.equals(elementType)) {
                UMLSystemBoundary umlSystemBoundary = parseSystemBoundary(jsonObject);
                umlSystemBoundaryMap.put(umlSystemBoundary.getJSONElementID(), umlSystemBoundary);
                allUmlElementsMap.put(umlSystemBoundary.getJSONElementID(), umlSystemBoundary);
                umlElement = umlSystemBoundary;
            }
            else if (UMLActor.UML_ACTOR_TYPE.equals(elementType)) {
                UMLActor umlActor = parseActor(jsonObject);
                umlActorMap.put(umlActor.getJSONElementID(), umlActor);
                allUmlElementsMap.put(umlActor.getJSONElementID(), umlActor);
                umlElement = umlActor;
            }
            else if (UMLUseCase.UML_USE_CASE_TYPE.equals(elementType)) {
                UMLUseCase umlUseCase = parseUseCase(jsonObject);
                umlUseCaseMap.put(umlUseCase.getJSONElementID(), umlUseCase);
                allUmlElementsMap.put(umlUseCase.getJSONElementID(), umlUseCase);
                umlElement = umlUseCase;
            }
            if (jsonObject.has(ELEMENT_OWNER) && !jsonObject.get(ELEMENT_OWNER).isJsonNull() && umlElement != null) {
                String ownerId = jsonObject.get(ELEMENT_OWNER).getAsString();
                ownerRelationships.put(umlElement, ownerId);
            }
        }

        // now we can resolve the owners: for this diagram type, only uml system boundaries can be the actual owner
        for (var ownerEntry : ownerRelationships.entrySet()) {
            String ownerId = ownerEntry.getValue();
            UMLElement umlElement = ownerEntry.getKey();
            UMLSystemBoundary parentSystemBoundary = umlSystemBoundaryMap.get(ownerId);
            umlElement.setParentElement(parentSystemBoundary);
        }

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLUseCaseAssociation> useCaseAssociation = parseUseCaseAssociation(rel.getAsJsonObject(), allUmlElementsMap);
            useCaseAssociation.ifPresent(umlUseCaseAssociationList::add);
        }

        return new UMLUseCaseDiagram(modelSubmissionId, new ArrayList<>(umlSystemBoundaryMap.values()), new ArrayList<>(umlActorMap.values()),
                new ArrayList<>(umlUseCaseMap.values()), umlUseCaseAssociationList);
    }

    /**
     * Parses the given JSON representation of a UML relationship to a UMLUseCaseAssociation Java object.
     *
     * @param relationshipJson  the JSON object containing the relationship
     * @param allUmlElementsMap a map containing all objects of the corresponding use case diagram, necessary for assigning source and target element of the relationships
     * @return the UMLUseCaseAssociation object parsed from the JSON object
     * @throws IOException when no object could be found in the allUmlElementsMap for the source and target ID in the JSON object
     */
    private static Optional<UMLUseCaseAssociation> parseUseCaseAssociation(JsonObject relationshipJson, Map<String, UMLElement> allUmlElementsMap) throws IOException {

        String relationshipType = relationshipJson.get(RELATIONSHIP_TYPE).getAsString();
        relationshipType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, relationshipType);

        if (!EnumUtils.isValidEnum(UMLUseCaseAssociation.UMLUseCaseAssociationType.class, relationshipType)) {
            return Optional.empty();
        }

        var associationType = UMLUseCaseAssociation.UMLUseCaseAssociationType.valueOf(relationshipType);
        String name = null;
        if (associationType == UMLUseCaseAssociation.UMLUseCaseAssociationType.USE_CASE_ASSOCIATION) {
            name = relationshipJson.get(ELEMENT_NAME).getAsString();
        }

        UMLElement source = UMLModelParser.findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_SOURCE);
        UMLElement target = UMLModelParser.findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            UMLUseCaseAssociation newComponentRelationship = new UMLUseCaseAssociation(name, source, target, associationType, relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newComponentRelationship);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

    /**
     * Parses the given JSON representation of a UML system boundary to a UMLSystemBoundary Java object.
     *
     * @param componentJson the JSON object containing the system boundary
     * @return the UMLSystemBoundary object parsed from the JSON object
     */
    private static UMLSystemBoundary parseSystemBoundary(JsonObject componentJson) {
        String systemBoundaryName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLSystemBoundary(systemBoundaryName, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML actor to a UMLActor Java object.
     *
     * @param componentJson the JSON object containing the actor
     * @return the UMLActor object parsed from the JSON object
     */
    private static UMLActor parseActor(JsonObject componentJson) {
        String actorName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLActor(actorName, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML use case to a UMLUseCase Java object.
     *
     * @param componentJson the JSON object containing the use case
     * @return the UMLUseCase object parsed from the JSON object
     */
    private static UMLUseCase parseUseCase(JsonObject componentJson) {
        String useCaseName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLUseCase(useCaseName, componentJson.get(ELEMENT_ID).getAsString());
    }

}
