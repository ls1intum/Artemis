package de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_MESSAGES;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.modeling.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.communication.Direction;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.communication.UMLCommunicationDiagram;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.communication.UMLCommunicationLink;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.communication.UMLMessage;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.object.UMLObject;
import de.tum.cit.aet.artemis.modeling.service.compass.umlmodel.parsers.UMLModelParser;

public class CommunicationDiagramParser {

    /**
     * Create a UML communication diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * communication diagram containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML communication diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static UMLCommunicationDiagram buildCommunicationDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<UMLCommunicationLink> umlCommunicationLinkList = new ArrayList<>();
        Map<String, UMLObject> umlObjectMap = ObjectDiagramParser.parseUMLObjects(modelElements);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLCommunicationLink> communicationLink = parseCommunicationLink(rel.getAsJsonObject(), umlObjectMap);
            communicationLink.ifPresent(umlCommunicationLinkList::add);
        }

        return new UMLCommunicationDiagram(modelSubmissionId, new ArrayList<>(umlObjectMap.values()), umlCommunicationLinkList);
    }

    /**
     * Parses the given JSON representation of a UML relationship to a UMLCommunicationLink Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param objectMap        a map containing all objects of the corresponding communication diagram, necessary for assigning source and target element of the relationships
     * @return the UMLCommunicationLink object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<UMLCommunicationLink> parseCommunicationLink(JsonObject relationshipJson, Map<String, UMLObject> objectMap) throws IOException {

        UMLObject source = UMLModelParser.findElement(relationshipJson, objectMap, RELATIONSHIP_SOURCE);
        UMLObject target = UMLModelParser.findElement(relationshipJson, objectMap, RELATIONSHIP_TARGET);

        List<UMLMessage> messages = new ArrayList<>();

        for (JsonElement messageJson : relationshipJson.getAsJsonArray(RELATIONSHIP_MESSAGES)) {
            JsonObject messageJsonObject = (JsonObject) messageJson;
            Direction direction = "target".equals(messageJsonObject.get("direction").getAsString()) ? Direction.TARGET : Direction.SOURCE;
            UMLMessage message = new UMLMessage(messageJsonObject.get("name").getAsString(), direction);
            messages.add(message);
        }

        if (source != null && target != null) {
            UMLCommunicationLink newCommunicationLink = new UMLCommunicationLink(source, target, messages, relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newCommunicationLink);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

}
