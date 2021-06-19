package de.tum.in.www1.artemis.service.compass.umlmodel.parsers;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.*;

import java.io.IOException;
import java.util.*;

import javax.validation.constraints.NotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.service.compass.umlmodel.activity.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.flowchart.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.object.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.usecase.*;

public class ObjectDiagramParser {

    /**
     * Create a UML object diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * object diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML object diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static UMLObjectDiagram buildObjectDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<UMLObjectLink> umlObjectLinkList = new ArrayList<>();
        Map<String, UMLObject> umlObjectMap = parseUMLObjects(modelElements);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLObjectLink> communicationLink = parseObjectLink(rel.getAsJsonObject(), umlObjectMap);
            communicationLink.ifPresent(umlObjectLinkList::add);
        }

        return new UMLObjectDiagram(modelSubmissionId, new ArrayList<>(umlObjectMap.values()), umlObjectLinkList);
    }

    /**
     * Parses the given JSON representation of a UML object list to a UMLObject Java map.
     *
     * @param modelElements a JSON array containing all the model elements of the corresponding UML object diagram as JSON objects
     * @return the UMLObject map parsed from the JSON array
     */
    @NotNull
    public static Map<String, UMLObject> parseUMLObjects(JsonArray modelElements) {
        Map<String, UMLObject> umlObjectMap = new HashMap<>();
        // loop over all JSON elements and create the UML objects
        for (JsonElement elem : modelElements) {
            JsonObject element = elem.getAsJsonObject();
            String elementType = element.get(ELEMENT_TYPE).getAsString();
            if ("ObjectName".equals(elementType)) {
                UMLObject umlObject = parseObject(element, modelElements);
                umlObjectMap.put(umlObject.getJSONElementID(), umlObject);
            }
        }
        return umlObjectMap;
    }

    /**
     * Parses the given JSON representation of a UML object to a UMLObject Java object.
     *
     * @param objectJson the JSON object containing the UML object
     * @param modelElements a JSON array containing all the model elements of the corresponding UML object diagram as JSON objects
     * @return the UMLObject object parsed from the JSON object
     */
    private static UMLObject parseObject(JsonObject objectJson, JsonArray modelElements) {
        Map<String, JsonObject> jsonElementMap = UMLModelParser.generateJsonElementMap(modelElements);
        String objectName = objectJson.get(ELEMENT_NAME).getAsString();
        List<UMLObjectAttribute> umlAttributesList = parseUmlAttributes(objectJson, jsonElementMap);
        List<UMLObjectMethod> umlMethodList = parseUmlMethods(objectJson, jsonElementMap);
        return new UMLObject(objectName, umlAttributesList, umlMethodList, objectJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a UMLObjectLink Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param objectMap a map containing all objects of the corresponding object diagram, necessary for assigning source and target element of the relationships
     * @return the UMLObjectLink object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<UMLObjectLink> parseObjectLink(JsonObject relationshipJson, Map<String, UMLObject> objectMap) throws IOException {

        UMLObject source = UMLModelParser.findElement(relationshipJson, objectMap, RELATIONSHIP_SOURCE);
        UMLObject target = UMLModelParser.findElement(relationshipJson, objectMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            UMLObjectLink newCommunicationLink = new UMLObjectLink(source, target, relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newCommunicationLink);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

    /**
     * Parses the given JSON representation of a UML elements to a UMLObjectAttribute list Java object.
     *
     * @param objectJson the JSON object containing the UML object
     * @param jsonElementMap a map containing all the model elements and their ids of the corresponding UML object diagram as JSON objects
     * @return the list of UMLObjectAttribute parsed from the JSON object map
     */
    @NotNull
    protected static List<UMLObjectAttribute> parseUmlAttributes(JsonObject objectJson, Map<String, JsonObject> jsonElementMap) {
        List<UMLObjectAttribute> umlAttributesList = new ArrayList<>();
        for (JsonElement attributeId : objectJson.getAsJsonArray(ELEMENT_ATTRIBUTES)) {
            UMLObjectAttribute newAttr = parseAttribute(jsonElementMap.get(attributeId.getAsString()));
            umlAttributesList.add(newAttr);
        }
        return umlAttributesList;
    }

    /**
     * Parses the given JSON representation of a UML elements to a UMLObjectMethod list Java object.
     *
     * @param objectJson the JSON object containing the UML object
     * @param jsonElementMap a map containing all the model elements and their ids of the corresponding UML object diagram as JSON objects
     * @return the list of UMLObjectMethod parsed from the JSON object map
     */
    @NotNull
    protected static List<UMLObjectMethod> parseUmlMethods(JsonObject objectJson, Map<String, JsonObject> jsonElementMap) {
        List<UMLObjectMethod> umlMethodList = new ArrayList<>();
        for (JsonElement methodId : objectJson.getAsJsonArray(ELEMENT_METHODS)) {
            UMLObjectMethod newMethod = parseMethod(jsonElementMap.get(methodId.getAsString()));
            umlMethodList.add(newMethod);
        }
        return umlMethodList;
    }

    /**
     * Parses the given JSON representation of a UML attribute to a UMLObjectAttribute Java object.
     *
     * @param attributeJson the JSON object containing the attribute
     * @return the UMLObjectAttribute object parsed from the JSON object
     */
    private static UMLObjectAttribute parseAttribute(JsonObject attributeJson) {
        String[] attributeNameArray = attributeJson.get(ELEMENT_NAME).getAsString().replaceAll("\\s+", "").split(":");
        String attributeName = attributeNameArray[0];
        String attributeType = "";

        if (attributeNameArray.length == 2) {
            attributeType = attributeNameArray[1];
        }

        return new UMLObjectAttribute(attributeName, attributeType, attributeJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML method to a UMLObjectMethod Java object.
     *
     * @param methodJson the JSON object containing the method
     * @return the UMLObjectMethod object parsed from the JSON object
     */
    private static UMLObjectMethod parseMethod(JsonObject methodJson) {
        String completeMethodName = methodJson.get(ELEMENT_NAME).getAsString();
        String[] methodEntryArray = completeMethodName.replaceAll("\\s+", "").split(":");
        String[] methodParts = methodEntryArray[0].split("[()]");

        String methodName = "";
        if (methodParts.length > 0) {
            methodName = methodParts[0];
        }

        String[] methodParams = {};
        if (methodParts.length == 2) {
            methodParams = methodParts[1].split(",");
        }

        String methodReturnType = "";
        if (methodEntryArray.length == 2) {
            methodReturnType = methodEntryArray[1];
        }

        return new UMLObjectMethod(completeMethodName, methodName, methodReturnType, Arrays.asList(methodParams), methodJson.get(ELEMENT_ID).getAsString());
    }
}
