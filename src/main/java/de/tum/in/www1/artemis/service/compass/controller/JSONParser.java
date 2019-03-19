package de.tum.in.www1.artemis.service.compass.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLAssociation;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLMethod;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;
import de.tum.in.www1.artemis.service.compass.utils.JSONMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONParser {

    private final static Logger log = LoggerFactory.getLogger(JSONParser.class);

    /**
     * Process a json object retrieved from a json formatted file to retrieve an UML model
     * TODO adapt the parser to support different UML diagrams
     *
     * @param root the json object of an UML diagram
     * @param modelId the Id of the model
     * @return the model as java object
     * @throws IOException on unexpected json formats
     */
    public static UMLModel buildModelFromJSON(JsonObject root, long modelId) throws IOException {
        JsonObject entities = root.getAsJsonObject(JSONMapping.elements);
        JsonArray allElementIds = entities.getAsJsonArray(JSONMapping.idArray);
        JsonObject entitiesById = entities.getAsJsonObject(JSONMapping.byId);

        JsonObject relationships = root.getAsJsonObject(JSONMapping.relationships);
        JsonArray allRelationshipIds = relationships.getAsJsonArray(JSONMapping.idArray);
        JsonObject relationshipsById = relationships.getAsJsonObject(JSONMapping.byId);

        Map<String, UMLClass> umlClassMap = new HashMap<>();
        List<UMLAssociation> umlAssociationList = new ArrayList<>();

        // <editor-fold desc="iterate over every class">
        for (JsonElement elementId : allElementIds) {
            JsonObject connectable = entitiesById.getAsJsonObject(elementId.getAsString());

            String className = connectable.get(JSONMapping.elementName).getAsString();

            List<UMLAttribute> umlAttributesList = new ArrayList<>();
            List<UMLMethod> umlMethodList = new ArrayList<>();

            for (JsonElement attributeElement : connectable.getAsJsonArray(JSONMapping.elementAttributes)) {
                JsonObject attribute = attributeElement.getAsJsonObject();

                String[] attributeNameArray = attribute.get(JSONMapping.elementName).getAsString().replaceAll(" ", "").split(":");
                String attributeName = attributeNameArray[0];
                String attributeType = "";
                if (attributeNameArray.length == 2) {
                    attributeType = attributeNameArray[1];
                }
                UMLAttribute newAttr = new UMLAttribute(attributeName, attributeType, attribute.get(JSONMapping.elementID).getAsString());
                umlAttributesList.add(newAttr);
            }

            for (JsonElement methodElement : connectable.getAsJsonArray(JSONMapping.elementMethods)) {
                JsonObject method = methodElement.getAsJsonObject();

                String completeMethodName = method.get(JSONMapping.elementName).getAsString();

                String[] methodEntryArray = completeMethodName.replaceAll(" ", "").split(":");
                String[] methodParts = methodEntryArray[0].split("[()]");

                if (methodParts.length < 1) {
                    break;
                }

                String methodName = methodParts[0];

                String[] methodParams = {};

                if (methodParts.length == 2) {
                    methodParams = methodParts[1].split(",");
                }

                String methodReturnType = "";
                if (methodEntryArray.length == 2) {
                    methodReturnType = methodEntryArray[1];
                }

                UMLMethod newMethod = new UMLMethod(completeMethodName, methodName, methodReturnType, Arrays.asList(methodParams), method.get(JSONMapping.elementID).getAsString());
                umlMethodList.add(newMethod);
            }

            UMLClass newClass = new UMLClass(className, umlAttributesList, umlMethodList, connectable.get(JSONMapping.elementID).getAsString(),
                connectable.get(JSONMapping.relationshipType).getAsString());

            //set parent class in attributes and methods
            for (UMLAttribute attribute : umlAttributesList) {
                attribute.setParentClass(newClass);
            }

            for (UMLMethod method: umlMethodList) {
                method.setParentClass(newClass);
            }

            umlClassMap.put(newClass.getJSONElementID(), newClass);
        }

        // </editor-fold>

        // <editor-fold desc="iterate over every relationship">
        for (JsonElement relationshipElement : allRelationshipIds) {
            JsonObject relationship = relationshipsById.getAsJsonObject(relationshipElement.getAsString());

            JsonObject relationshipSource = relationship.getAsJsonObject(JSONMapping.relationshipSource);
            JsonObject relationshipTarget = relationship.getAsJsonObject(JSONMapping.relationshipTarget);

            String sourceJSONID = relationshipSource.get(JSONMapping.relationshipEndpointID).getAsString();
            String targetJSONID = relationshipTarget.get(JSONMapping.relationshipEndpointID).getAsString();

            UMLClass source = umlClassMap.get(sourceJSONID);
            UMLClass target = umlClassMap.get(targetJSONID);

            JsonElement relationshipSourceRole = relationshipSource.has(JSONMapping.relationshipRole) ?
                relationshipSource.get(JSONMapping.relationshipRole) : JsonNull.INSTANCE;
            JsonElement relationshipTargetRole = relationshipTarget.has(JSONMapping.relationshipRole) ?
                relationshipTarget.get(JSONMapping.relationshipRole) : JsonNull.INSTANCE;
            JsonElement relationshipSourceMultiplicity = relationshipSource.has(JSONMapping.relationshipMultiplicity) ?
                relationshipSource.get(JSONMapping.relationshipMultiplicity) : JsonNull.INSTANCE;
            JsonElement relationshipTargetMultiplicity = relationshipTarget.has(JSONMapping.relationshipMultiplicity) ?
                relationshipTarget.get(JSONMapping.relationshipMultiplicity) : JsonNull.INSTANCE;

            if (source != null && target != null) {
                UMLAssociation newRelation = new UMLAssociation(source, target,
                    relationship.get(JSONMapping.relationshipType).getAsString(),
                    relationship.get(JSONMapping.elementID).getAsString(),
                    relationshipSourceRole.isJsonNull() ? "" : relationshipSourceRole.getAsString(),
                    relationshipTargetRole.isJsonNull() ? "" : relationshipTargetRole.getAsString(),
                    relationshipSourceMultiplicity.isJsonNull() ? "" : relationshipSourceMultiplicity.getAsString(),
                    relationshipTargetMultiplicity.isJsonNull() ? "" : relationshipTargetMultiplicity.getAsString());
                umlAssociationList.add(newRelation);
            } else {
                throw new IOException("Relationship source or target not part of model!");
            }
        }

        // </editor-fold>

        return new UMLModel(new ArrayList<>(umlClassMap.values()), umlAssociationList, modelId);
    }
}

