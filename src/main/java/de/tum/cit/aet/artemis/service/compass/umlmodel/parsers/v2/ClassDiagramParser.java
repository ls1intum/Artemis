package de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.v2;

import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_ATTRIBUTES;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_ID;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_METHODS;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_NAME;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_OWNER;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.ELEMENT_TYPE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_ENDPOINT_ID;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_MULTIPLICITY;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_ROLE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_SOURCE;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TARGET;
import static de.tum.cit.aet.artemis.service.compass.utils.JSONMapping.RELATIONSHIP_TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.EnumUtils;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.cit.aet.artemis.service.compass.umlmodel.UMLElement;
import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLPackage;
import de.tum.cit.aet.artemis.service.compass.umlmodel.classdiagram.UMLRelationship;
import de.tum.cit.aet.artemis.service.compass.umlmodel.parsers.UMLModelParser;

public class ClassDiagramParser {

    /**
     * Create a UML class diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a class
     * diagram containing these UML model elements.
     *
     * @param modelElements     the model elements as JSON array
     * @param relationships     the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML class diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    protected static UMLClassDiagram buildClassDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        Map<String, UMLClass> umlClassMap = new HashMap<>();
        List<UMLRelationship> umlRelationshipList = new ArrayList<>();
        Map<String, UMLPackage> umlPackageMap = new HashMap<>();

        // loop over all JSON elements and UML package objects
        for (JsonElement elem : modelElements) {
            JsonObject element = elem.getAsJsonObject();
            String elementType = element.get(ELEMENT_TYPE).getAsString();

            if (elementType.equals(UMLPackage.UML_PACKAGE_TYPE)) {
                UMLPackage umlPackage = parsePackage(element);
                umlPackageMap.put(umlPackage.getJSONElementID(), umlPackage);
            }
        }

        // loop over all JSON elements and create the UML class objects
        for (JsonElement elem : modelElements) {
            JsonObject element = elem.getAsJsonObject();

            String elementType = element.get(ELEMENT_TYPE).getAsString();
            elementType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, elementType);

            if (EnumUtils.isValidEnum(UMLClass.UMLClassType.class, elementType)) {
                UMLClass umlClass = parseClass(elementType, element, modelElements, umlPackageMap);
                umlClassMap.put(umlClass.getJSONElementID(), umlClass);
            }
        }

        // loop over all JSON relationship elements and create UML relationships
        for (JsonElement rel : relationships) {
            Optional<UMLRelationship> relationship = parseRelationship(rel.getAsJsonObject(), umlClassMap, umlPackageMap);
            relationship.ifPresent(umlRelationshipList::add);
        }

        return new UMLClassDiagram(modelSubmissionId, new ArrayList<>(umlClassMap.values()), umlRelationshipList, new ArrayList<>(umlPackageMap.values()));
    }

    /**
     * Parses the given JSON representation of a UML package to a UMLPackage Java object.
     *
     * @param packageJson the JSON object containing the package
     * @return the UMLPackage object parsed from the JSON object
     */
    private static UMLPackage parsePackage(JsonObject packageJson) {
        String packageName = packageJson.get(ELEMENT_NAME).getAsString();
        List<UMLElement> umlClassList = new ArrayList<>();
        String jsonElementId = packageJson.get(ELEMENT_ID).getAsString();
        return new UMLPackage(packageName, umlClassList, jsonElementId);
    }

    /**
     * Parses the given JSON representation of a UML class to a UMLClass Java object.
     *
     * @param classType     the type of the UML class
     * @param classJson     the JSON object containing the UML class
     * @param modelElements a JSON array containing all the model elements of the corresponding UML class diagram as JSON objects
     * @param umlPackageMap a map containing all the packages of the corresponding UML class diagram
     * @return the UMLClass object parsed from the JSON object
     */
    private static UMLClass parseClass(String classType, JsonObject classJson, JsonArray modelElements, Map<String, UMLPackage> umlPackageMap) {
        Map<String, JsonObject> jsonElementMap = UMLModelParser.generateJsonElementMap(modelElements);

        UMLClass.UMLClassType umlClassType = UMLClass.UMLClassType.valueOf(classType);
        String className = classJson.get(ELEMENT_NAME).getAsString();

        List<UMLAttribute> umlAttributesList = parseUmlAttributes(classJson, jsonElementMap);
        List<UMLMethod> umlMethodList = parseUmlMethods(classJson, jsonElementMap);

        UMLClass newClass = new UMLClass(className, umlAttributesList, umlMethodList, classJson.get(ELEMENT_ID).getAsString(), umlClassType);

        if (classJson.has(ELEMENT_OWNER) && !classJson.get(ELEMENT_OWNER).isJsonNull()) {
            String packageId = classJson.get(ELEMENT_OWNER).getAsString();
            UMLPackage umlPackage = umlPackageMap.get(packageId);
            if (umlPackage != null) {
                umlPackage.addSubElement(newClass);
                newClass.setUmlPackage(umlPackage);
            }
        }

        return newClass;
    }

    /**
     * Parses the given JSON representation of a UML relationship to a UMLRelationship Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param classMap         a map containing all classes of the corresponding class diagram, necessary for assigning source and target element of the relationships
     * @param packageMap       the package map contains all packages of the diagram
     * @return the UMLRelationship object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<UMLRelationship> parseRelationship(JsonObject relationshipJson, Map<String, UMLClass> classMap, Map<String, UMLPackage> packageMap) throws IOException {
        String relationshipType = relationshipJson.get(RELATIONSHIP_TYPE).getAsString();
        relationshipType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, relationshipType);

        if (!EnumUtils.isValidEnum(UMLRelationship.UMLRelationshipType.class, relationshipType)) {
            return Optional.empty();
        }

        JsonObject relationshipSource = relationshipJson.getAsJsonObject(RELATIONSHIP_SOURCE);
        JsonObject relationshipTarget = relationshipJson.getAsJsonObject(RELATIONSHIP_TARGET);

        UMLClass source = UMLModelParser.findElement(relationshipJson, classMap, RELATIONSHIP_SOURCE);
        UMLClass target = UMLModelParser.findElement(relationshipJson, classMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            var type = UMLRelationship.UMLRelationshipType.valueOf(relationshipType);
            var jsonElementId = relationshipJson.get(ELEMENT_ID).getAsString();
            var sourceRole = relationshipSource.has(RELATIONSHIP_ROLE) ? relationshipSource.get(RELATIONSHIP_ROLE).getAsString() : null;
            var targetRole = relationshipTarget.has(RELATIONSHIP_ROLE) ? relationshipTarget.get(RELATIONSHIP_ROLE).getAsString() : null;
            var sourceMultiplicity = relationshipSource.has(RELATIONSHIP_MULTIPLICITY) ? relationshipSource.get(RELATIONSHIP_MULTIPLICITY).getAsString() : null;
            var targetMultiplicity = relationshipTarget.has(RELATIONSHIP_MULTIPLICITY) ? relationshipTarget.get(RELATIONSHIP_MULTIPLICITY).getAsString() : null;
            UMLRelationship newRelationship = new UMLRelationship(source, target, type, jsonElementId, sourceRole, targetRole, sourceMultiplicity, targetMultiplicity);
            return Optional.of(newRelationship);
        }
        else {
            String sourceJSONID = relationshipSource.get(RELATIONSHIP_ENDPOINT_ID).getAsString();
            String targetJSONID = relationshipTarget.get(RELATIONSHIP_ENDPOINT_ID).getAsString();
            if (source == null && packageMap.containsKey(sourceJSONID) || target == null && packageMap.containsKey(targetJSONID)) {
                // workaround: prevent exception when a package is source or target of a relationship
                return Optional.empty();
            }

            throw new IOException("Relationship source or target not part of model!");
        }
    }

    /**
     * Finds the ids of attributes in JSON representation of model and matches them to elements in map and returns the list of UMLAttribute Java objects
     *
     * @param classJson      the json representation of model
     * @param jsonElementMap map of element ids and elements in the model
     * @return the list of UMLAttribute java objects
     */
    @NotNull
    protected static List<UMLAttribute> parseUmlAttributes(JsonObject classJson, Map<String, JsonObject> jsonElementMap) {
        List<UMLAttribute> umlAttributesList = new ArrayList<>();
        for (JsonElement attributeId : classJson.getAsJsonArray(ELEMENT_ATTRIBUTES)) {
            UMLAttribute newAttr = parseAttribute(jsonElementMap.get(attributeId.getAsString()));
            umlAttributesList.add(newAttr);
        }
        return umlAttributesList;
    }

    /**
     * Finds the ids of methods in JSON representation of model and matches them to elements in map and returns the list of UMLMethod Java objects
     *
     * @param objectJson     the json representation of model
     * @param jsonElementMap map of element ids and elements in the model
     * @return the list of UMLMethod Java objects
     */
    @NotNull
    protected static List<UMLMethod> parseUmlMethods(JsonObject objectJson, Map<String, JsonObject> jsonElementMap) {
        List<UMLMethod> umlMethodList = new ArrayList<>();
        for (JsonElement methodId : objectJson.getAsJsonArray(ELEMENT_METHODS)) {
            UMLMethod newMethod = parseMethod(jsonElementMap.get(methodId.getAsString()));
            umlMethodList.add(newMethod);
        }
        return umlMethodList;
    }

    /**
     * Parses the given JSON representation of a UML attribute to a UMLAttribute Java object.
     *
     * @param attributeJson the JSON object containing the attribute
     * @return the UMLAttribute object parsed from the JSON object
     */
    protected static UMLAttribute parseAttribute(JsonObject attributeJson) {
        String[] attributeNameArray = attributeJson.get(ELEMENT_NAME).getAsString().replaceAll("\\s+", "").split(":");
        String attributeName = attributeNameArray[0];
        String attributeType = "";

        if (attributeNameArray.length == 2) {
            attributeType = attributeNameArray[1];
        }

        return new UMLAttribute(attributeName, attributeType, attributeJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML method to a UMLMethod Java object.
     *
     * @param methodJson the JSON object containing the method
     * @return the UMLMethod object parsed from the JSON object
     */
    protected static UMLMethod parseMethod(JsonObject methodJson) {
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

        return new UMLMethod(completeMethodName, methodName, methodReturnType, Arrays.asList(methodParams), methodJson.get(ELEMENT_ID).getAsString());
    }

}
