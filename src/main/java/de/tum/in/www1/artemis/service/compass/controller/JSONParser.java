package de.tum.in.www1.artemis.service.compass.controller;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassModel;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassRelationship;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLPackage;
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
    // TODO CZ: refactor this (extract buildModelFromJSON to specific UML classes, e.g. UMLClass.buildModelFromJSON() to get the parsed class)
    public static UMLClassModel buildModelFromJSON(JsonObject root, long modelId) throws IOException {
        JsonArray elements = root.getAsJsonArray(JSONMapping.elements);
        Map<String, JsonObject> jsonElementMap = generateJsonElementMap(elements);

        JsonArray relationships = root.getAsJsonArray(JSONMapping.relationships);

        Map<String, UMLClass> umlClassMap = new HashMap<>();
        List<UMLClassRelationship> umlAssociationList = new ArrayList<>();
        Map<String, UMLPackage> umlPackageMap = new HashMap<>();

        // <editor-fold desc="iterate over every package">
        for (JsonElement elem : elements) {
            JsonObject element = elem.getAsJsonObject();

            String elementType = element.get(JSONMapping.elementType).getAsString();
            if (elementType.equals(UMLPackage.UML_PACKAGE_TYPE)) {
                String packageName = element.get(JSONMapping.elementName).getAsString();

                List<UMLClass> umlClassList = new ArrayList<>();
                String jsonElementId = element.get(JSONMapping.elementID).getAsString();
                UMLPackage umlPackage = new UMLPackage(packageName, umlClassList, jsonElementId);
                umlPackageMap.put(jsonElementId, umlPackage);
            }
        }
        // </editor-fold>

        // <editor-fold desc="iterate over every element (classes, attributes, methods)">
        for (JsonElement elem : elements) {
            JsonObject element = elem.getAsJsonObject();

            String elementType = element.get(JSONMapping.elementType).getAsString();
            if (UMLClassType.getTypesAsList().contains(elementType))
            {
                String className = element.get(JSONMapping.elementName).getAsString();

                List<UMLAttribute> umlAttributesList = new ArrayList<>();
                for (JsonElement attributeId : element.getAsJsonArray(JSONMapping.elementAttributes)) {
                    JsonObject attribute = jsonElementMap.get(attributeId.getAsString());

                    String[] attributeNameArray = attribute.get(JSONMapping.elementName).getAsString()
                        .replaceAll(" ", "").split(":");
                    String attributeName = attributeNameArray[0];
                    String attributeType = "";
                    if (attributeNameArray.length == 2) {
                        attributeType = attributeNameArray[1];
                    }
                    UMLAttribute newAttr = new UMLAttribute(attributeName, attributeType,
                        attribute.get(JSONMapping.elementID).getAsString());
                    umlAttributesList.add(newAttr);
                }

                List<UMLMethod> umlMethodList = new ArrayList<>();
                for (JsonElement methodId : element.getAsJsonArray(JSONMapping.elementMethods)) {
                    JsonObject method = jsonElementMap.get(methodId.getAsString());

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

                UMLClass newClass = new UMLClass(className, umlAttributesList, umlMethodList,
                    element.get(JSONMapping.elementID).getAsString(),
                    CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, elementType));

                if (element.has(JSONMapping.elementOwner) && !element.get(JSONMapping.elementOwner).isJsonNull()) {
                    String packageId = element.get(JSONMapping.elementOwner).getAsString();
                    UMLPackage umlPackage = umlPackageMap.get(packageId);
                    if (umlPackage != null) {
                        umlPackage.addClass(newClass);
                        newClass.setUmlPackage(umlPackage);
                    }
                }

                //set parent class in attributes and methods
                for (UMLAttribute attribute : umlAttributesList) {
                    attribute.setParentClass(newClass);
                }

                for (UMLMethod method: umlMethodList) {
                    method.setParentClass(newClass);
                }

                umlClassMap.put(newClass.getJSONElementID(), newClass);
            }
        }

        // </editor-fold>

        // <editor-fold desc="iterate over every relationship">
        for (JsonElement rel : relationships) {
            JsonObject relationship = rel.getAsJsonObject();

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

            String relationshipType = relationship.get(JSONMapping.relationshipType).getAsString();
            relationshipType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, relationshipType);

            if (source != null && target != null) {
                UMLClassRelationship newRelation = new UMLClassRelationship(source, target, relationshipType,
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

        return new UMLClassModel(new ArrayList<>(umlPackageMap.values()), new ArrayList<>(umlClassMap.values()), umlAssociationList, modelId);
    }

    private static Map<String, JsonObject> generateJsonElementMap(JsonArray elements) {
        Map<String, JsonObject> jsonElementMap = new HashMap<>();
        elements.forEach(
            element -> jsonElementMap.put(element.getAsJsonObject().get("id").getAsString(), element.getAsJsonObject())
        );
        return jsonElementMap;
    }
}

