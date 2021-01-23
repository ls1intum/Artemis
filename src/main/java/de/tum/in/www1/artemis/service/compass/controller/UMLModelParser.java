package de.tum.in.www1.artemis.service.compass.controller;

import static de.tum.in.www1.artemis.service.compass.utils.JSONMapping.*;

import java.io.IOException;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.EnumUtils;

import com.google.common.base.CaseFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode.UMLActivityNodeType;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship.UMLRelationshipType;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.Direction;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLCommunicationDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLCommunicationLink;
import de.tum.in.www1.artemis.service.compass.umlmodel.communication.UMLMessage;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponent;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponentDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponentInterface;
import de.tum.in.www1.artemis.service.compass.umlmodel.component.UMLComponentRelationship;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.UMLArtifact;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.UMLDeploymentDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.deployment.UMLNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.flowchart.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.object.UMLObject;
import de.tum.in.www1.artemis.service.compass.umlmodel.object.UMLObjectDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.object.UMLObjectLink;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNet;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetArc;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetPlace;
import de.tum.in.www1.artemis.service.compass.umlmodel.petrinet.PetriNetTransition;
import de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree.SyntaxTree;
import de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree.SyntaxTreeLink;
import de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree.SyntaxTreeNonterminal;
import de.tum.in.www1.artemis.service.compass.umlmodel.syntaxtree.SyntaxTreeTerminal;
import de.tum.in.www1.artemis.service.compass.umlmodel.usecase.*;

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
            case ClassDiagram -> buildClassDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case ActivityDiagram -> buildActivityDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case UseCaseDiagram -> buildUseCaseDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case CommunicationDiagram -> buildCommunicationDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case ComponentDiagram -> buildComponentDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case DeploymentDiagram -> buildDeploymentDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case ObjectDiagram -> buildObjectDiagramFromJSON(modelElements, relationships, modelSubmissionId);
            case PetriNet -> buildPetriNetFromJSON(modelElements, relationships, modelSubmissionId);
            case SyntaxTree -> buildSyntaxTreeFromJSON(modelElements, relationships, modelSubmissionId);
            case Flowchart -> buildFlowchartFromJSON(modelElements, relationships, modelSubmissionId);
        };
    }

    /**
     * Create a UML communication diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * communication diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML communication diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static UMLCommunicationDiagram buildCommunicationDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<UMLCommunicationLink> umlCommunicationLinkList = new ArrayList<>();
        Map<String, UMLObject> umlObjectMap = parseUMLObjects(modelElements);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLCommunicationLink> communicationLink = parseCommunicationLink(rel.getAsJsonObject(), umlObjectMap);
            communicationLink.ifPresent(umlCommunicationLinkList::add);
        }

        return new UMLCommunicationDiagram(modelSubmissionId, new ArrayList<>(umlObjectMap.values()), umlCommunicationLinkList);
    }

    /**
     * Create a UML use case diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * use case diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML use case diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static UMLUseCaseDiagram buildUseCaseDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
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
     * Create a UML object diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * object diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML object diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static UMLObjectDiagram buildObjectDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<UMLObjectLink> umlObjectLinkList = new ArrayList<>();
        Map<String, UMLObject> umlObjectMap = parseUMLObjects(modelElements);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLObjectLink> communicationLink = parseObjectLink(rel.getAsJsonObject(), umlObjectMap);
            communicationLink.ifPresent(umlObjectLinkList::add);
        }

        return new UMLObjectDiagram(modelSubmissionId, new ArrayList<>(umlObjectMap.values()), umlObjectLinkList);
    }

    @NotNull
    private static Map<String, UMLObject> parseUMLObjects(JsonArray modelElements) {
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
     * Create a UML component diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * component diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML component diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static UMLComponentDiagram buildComponentDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
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

    private static void findOwner(Map<UMLElement, String> ownerRelationships, JsonObject jsonObject, UMLElement umlElement) {
        if (jsonObject.has(ELEMENT_OWNER) && !jsonObject.get(ELEMENT_OWNER).isJsonNull()) {
            String ownerId = jsonObject.get(ELEMENT_OWNER).getAsString();
            ownerRelationships.put(umlElement, ownerId);
        }
    }

    /**
     * Create a UML deployment diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * deployment diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML deployment diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static UMLDeploymentDiagram buildDeploymentDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {

        // TODO: try to further reduce code duplication from buildComponentDiagramFromJSON

        Map<String, UMLComponent> umlComponentMap = new HashMap<>();
        Map<String, UMLComponentInterface> umlComponentInterfaceMap = new HashMap<>();
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
            if (UMLComponent.UML_COMPONENT_TYPE.equals(elementType)) {
                UMLComponent umlComponent = parseComponent(jsonObject);
                umlComponentMap.put(umlComponent.getJSONElementID(), umlComponent);
                umlElement = umlComponent;
            }
            // NOTE: there is a difference in the json between ComponentInterface and DeploymentInterface
            else if (UMLComponentInterface.UML_DEPLOYMENT_INTERFACE_TYPE.equals(elementType)) {
                UMLComponentInterface umlComponentInterface = parseComponentInterface(jsonObject);
                umlComponentInterfaceMap.put(umlComponentInterface.getJSONElementID(), umlComponentInterface);
                umlElement = umlComponentInterface;
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
                findOwner(ownerRelationships, jsonObject, umlElement);
            }
        }

        // now we can resolve the owners: for this diagram type, uml components and uml nodes can be the actual owner
        resolveParentComponent(allUmlElementsMap, ownerRelationships);

        // loop over all JSON control flow elements and create UML communication links
        for (JsonElement rel : relationships) {
            Optional<UMLComponentRelationship> componentRelationship = parseComponentRelationship(rel.getAsJsonObject(), allUmlElementsMap);
            componentRelationship.ifPresent(umlComponentRelationshipList::add);
        }

        return new UMLDeploymentDiagram(modelSubmissionId, new ArrayList<>(umlComponentMap.values()), new ArrayList<>(umlComponentInterfaceMap.values()),
                umlComponentRelationshipList, new ArrayList<>(umlNodeMap.values()), new ArrayList<>(umlArtifactMap.values()));
    }

    private static void resolveParentComponent(Map<String, UMLElement> allUmlElementsMap, Map<UMLElement, String> ownerRelationships) {
        for (var ownerEntry : ownerRelationships.entrySet()) {
            String ownerId = ownerEntry.getValue();
            UMLElement umlElement = ownerEntry.getKey();
            UMLElement parentComponent = allUmlElementsMap.get(ownerId);
            umlElement.setParentElement(parentComponent);
        }
    }

    /**
     * Create a UML class diagram from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a class
     * diagram containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML class diagram containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static UMLClassDiagram buildClassDiagramFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
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

            if (EnumUtils.isValidEnum(UMLClassType.class, elementType)) {
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
     * @param classType the type of the UML class
     * @param classJson the JSON object containing the UML class
     * @param modelElements a JSON array containing all the model elements of the corresponding UML class diagram as JSON objects
     * @param umlPackageMap a map containing all the packages of the corresponding UML class diagram
     * @return the UMLClass object parsed from the JSON object
     */
    private static UMLClass parseClass(String classType, JsonObject classJson, JsonArray modelElements, Map<String, UMLPackage> umlPackageMap) {
        Map<String, JsonObject> jsonElementMap = generateJsonElementMap(modelElements);

        UMLClassType umlClassType = UMLClassType.valueOf(classType);
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

    @NotNull
    private static List<UMLAttribute> parseUmlAttributes(JsonObject classJson, Map<String, JsonObject> jsonElementMap) {
        List<UMLAttribute> umlAttributesList = new ArrayList<>();
        for (JsonElement attributeId : classJson.getAsJsonArray(ELEMENT_ATTRIBUTES)) {
            UMLAttribute newAttr = parseAttribute(jsonElementMap.get(attributeId.getAsString()));
            umlAttributesList.add(newAttr);
        }
        return umlAttributesList;
    }

    @NotNull
    private static List<UMLMethod> parseUmlMethods(JsonObject objectJson, Map<String, JsonObject> jsonElementMap) {
        List<UMLMethod> umlMethodList = new ArrayList<>();
        for (JsonElement methodId : objectJson.getAsJsonArray(ELEMENT_METHODS)) {
            UMLMethod newMethod = parseMethod(jsonElementMap.get(methodId.getAsString()));
            umlMethodList.add(newMethod);
        }
        return umlMethodList;
    }

    /**
     * Parses the given JSON representation of a UML object to a UMLObject Java object.
     *
     * @param objectJson the JSON object containing the UML object
     * @param modelElements a JSON array containing all the model elements of the corresponding UML class diagram as JSON objects
     * @return the UMLObject object parsed from the JSON object
     */
    private static UMLObject parseObject(JsonObject objectJson, JsonArray modelElements) {
        Map<String, JsonObject> jsonElementMap = generateJsonElementMap(modelElements);
        String objectName = objectJson.get(ELEMENT_NAME).getAsString();
        List<UMLAttribute> umlAttributesList = parseUmlAttributes(objectJson, jsonElementMap);
        List<UMLMethod> umlMethodList = parseUmlMethods(objectJson, jsonElementMap);
        return new UMLObject(objectName, umlAttributesList, umlMethodList, objectJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML attribute to a UMLAttribute Java object.
     *
     * @param attributeJson the JSON object containing the attribute
     * @return the UMLAttribute object parsed from the JSON object
     */
    private static UMLAttribute parseAttribute(JsonObject attributeJson) {
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
    private static UMLMethod parseMethod(JsonObject methodJson) {
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

    /**
     * Parses the given JSON representation of a UML relationship to a UMLRelationship Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param classMap a map containing all classes of the corresponding class diagram, necessary for assigning source and target element of the relationships
     * @param packageMap the package map contains all packages of the diagram
     * @return the UMLRelationship object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<UMLRelationship> parseRelationship(JsonObject relationshipJson, Map<String, UMLClass> classMap, Map<String, UMLPackage> packageMap) throws IOException {
        String relationshipType = relationshipJson.get(RELATIONSHIP_TYPE).getAsString();
        relationshipType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, relationshipType);

        if (!EnumUtils.isValidEnum(UMLRelationshipType.class, relationshipType)) {
            return Optional.empty();
        }

        JsonObject relationshipSource = relationshipJson.getAsJsonObject(RELATIONSHIP_SOURCE);
        JsonObject relationshipTarget = relationshipJson.getAsJsonObject(RELATIONSHIP_TARGET);

        UMLClass source = findElement(relationshipJson, classMap, RELATIONSHIP_SOURCE);
        UMLClass target = findElement(relationshipJson, classMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            var type = UMLRelationshipType.valueOf(relationshipType);
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

    private static Optional<UMLComponentRelationship> parseComponentRelationship(JsonObject relationshipJson, Map<String, UMLElement> allUmlElementsMap) throws IOException {

        String relationshipType = relationshipJson.get(RELATIONSHIP_TYPE).getAsString();
        relationshipType = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, relationshipType);

        if (!EnumUtils.isValidEnum(UMLComponentRelationship.UMLComponentRelationshipType.class, relationshipType)) {
            return Optional.empty();
        }

        UMLElement source = findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_SOURCE);
        UMLElement target = findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            UMLComponentRelationship newComponentRelationship = new UMLComponentRelationship(source, target,
                    UMLComponentRelationship.UMLComponentRelationshipType.valueOf(relationshipType), relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newComponentRelationship);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

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

        UMLElement source = findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_SOURCE);
        UMLElement target = findElement(relationshipJson, allUmlElementsMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            UMLUseCaseAssociation newComponentRelationship = new UMLUseCaseAssociation(name, source, target, associationType, relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newComponentRelationship);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

    private static <T extends UMLElement> T findElement(JsonObject relationshipJson, Map<String, T> elementsMap, String jsonField) {
        JsonObject relationshipSource = relationshipJson.getAsJsonObject(jsonField);
        String sourceJSONID = relationshipSource.get(RELATIONSHIP_ENDPOINT_ID).getAsString();
        return elementsMap.get(sourceJSONID);
    }

    private static UMLComponentInterface parseComponentInterface(JsonObject componentInterfaceJson) {
        String componentInterfaceName = componentInterfaceJson.get(ELEMENT_NAME).getAsString();
        return new UMLComponentInterface(componentInterfaceName, componentInterfaceJson.get(ELEMENT_ID).getAsString());
    }

    private static UMLComponent parseComponent(JsonObject componentJson) {
        String componentName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLComponent(componentName, componentJson.get(ELEMENT_ID).getAsString());
    }

    private static UMLNode parseNode(JsonObject nodeJson) {
        String nodeName = nodeJson.get(ELEMENT_NAME).getAsString();
        String stereotypeName = nodeJson.get(STEREOTYPE_NAME).getAsString();
        return new UMLNode(nodeName, stereotypeName, nodeJson.get(ELEMENT_ID).getAsString());
    }

    private static UMLArtifact parseArtifact(JsonObject artifactJson) {
        String artifactName = artifactJson.get(ELEMENT_NAME).getAsString();
        return new UMLArtifact(artifactName, artifactJson.get(ELEMENT_ID).getAsString());
    }

    private static UMLSystemBoundary parseSystemBoundary(JsonObject componentJson) {
        String systemBoundaryName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLSystemBoundary(systemBoundaryName, componentJson.get(ELEMENT_ID).getAsString());
    }

    private static UMLActor parseActor(JsonObject componentJson) {
        String actorName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLActor(actorName, componentJson.get(ELEMENT_ID).getAsString());
    }

    private static UMLUseCase parseUseCase(JsonObject componentJson) {
        String useCaseName = componentJson.get(ELEMENT_NAME).getAsString();
        return new UMLUseCase(useCaseName, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a UMLCommunicationLink Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param objectMap a map containing all objects of the corresponding communication diagram, necessary for assigning source and target element of the relationships
     * @return the UMLCommunicationLink object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<UMLCommunicationLink> parseCommunicationLink(JsonObject relationshipJson, Map<String, UMLObject> objectMap) throws IOException {

        UMLObject source = findElement(relationshipJson, objectMap, RELATIONSHIP_SOURCE);
        UMLObject target = findElement(relationshipJson, objectMap, RELATIONSHIP_TARGET);

        List<UMLMessage> messages = new ArrayList<>();

        for (JsonElement messageJson : relationshipJson.getAsJsonArray(RELATIONSHIP_MESSAGES)) {
            JsonObject messageJsonObject = (JsonObject) messageJson;
            Direction direction = messageJsonObject.get("direction").getAsString().equals("target") ? Direction.TARGET : Direction.SOURCE;
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

    /**
     * Parses the given JSON representation of a UML relationship to a UMLObjectLink Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param objectMap a map containing all objects of the corresponding object diagram, necessary for assigning source and target element of the relationships
     * @return the UMLObjectLink object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<UMLObjectLink> parseObjectLink(JsonObject relationshipJson, Map<String, UMLObject> objectMap) throws IOException {

        UMLObject source = findElement(relationshipJson, objectMap, RELATIONSHIP_SOURCE);
        UMLObject target = findElement(relationshipJson, objectMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            UMLObjectLink newCommunicationLink = new UMLObjectLink(source, target, relationshipJson.get(ELEMENT_ID).getAsString());
            return Optional.of(newCommunicationLink);
        }
        else {
            throw new IOException("Relationship source or target not part of model!");
        }
    }

    /**
     * Create an activity diagram from the model and control flow elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates an activity
     * diagram containing these UML model elements.
     *
     * @param modelElements the model elements (UML activities and activity nodes) as JSON array
     * @param controlFlows the control flow elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a UML activity diagram containing the parsed model elements and control flows
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the control flow JSON objects
     */
    private static UMLActivityDiagram buildActivityDiagramFromJSON(JsonArray modelElements, JsonArray controlFlows, long modelSubmissionId) throws IOException {
        Map<String, UMLActivityElement> umlActivityElementMap = new HashMap<>();
        Map<String, UMLActivity> umlActivityMap = new HashMap<>();
        List<UMLActivityNode> umlActivityNodeList = new ArrayList<>();
        List<UMLControlFlow> umlControlFlowList = new ArrayList<>();

        // loop over all JSON elements and create activity and activity node objects
        for (JsonElement elem : modelElements) {
            JsonObject element = elem.getAsJsonObject();

            String elementType = element.get(ELEMENT_TYPE).getAsString();
            String elementTypeUpperUnderscore = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, elementType);

            if (EnumUtils.isValidEnum(UMLActivityNodeType.class, elementTypeUpperUnderscore)) {
                UMLActivityNode activityNode = parseActivityNode(elementTypeUpperUnderscore, element);
                umlActivityNodeList.add(activityNode);
                umlActivityElementMap.put(activityNode.getJSONElementID(), activityNode);
            }
            else if (UMLActivity.UML_ACTIVITY_TYPE.equals(elementType)) {
                UMLActivity activity = parseActivity(element);
                umlActivityMap.put(activity.getJSONElementID(), activity);
                umlActivityElementMap.put(activity.getJSONElementID(), activity);
            }
        }

        // loop over all JSON elements again to connect parent activity elements with their child elements
        for (JsonElement elem : modelElements) {
            JsonObject element = elem.getAsJsonObject();

            if (element.has(ELEMENT_OWNER) && !element.get(ELEMENT_OWNER).isJsonNull()) {
                String parentActivityId = element.get(ELEMENT_OWNER).getAsString();
                UMLActivity parentActivity = umlActivityMap.get(parentActivityId);
                UMLActivityElement childElement = umlActivityElementMap.get(element.get(ELEMENT_ID).getAsString());

                if (parentActivity != null && childElement != null) {
                    parentActivity.addChildElement(childElement);
                    childElement.setParentActivity(parentActivity);
                }
            }
        }

        // loop over all JSON control flow elements and create control flow objects
        for (JsonElement controlFlow : controlFlows) {
            UMLControlFlow newControlFlow = parseControlFlow(controlFlow.getAsJsonObject(), umlActivityElementMap);
            umlControlFlowList.add(newControlFlow);
        }

        return new UMLActivityDiagram(modelSubmissionId, umlActivityNodeList, new ArrayList<>(umlActivityMap.values()), umlControlFlowList);
    }

    /**
     * Parses the given JSON representation of a UML activity node to a UMLActivityNode Java object.
     *
     * @param activityNodeType the type of the activity node
     * @param activityNodeJson the JSON object containing the activity
     * @return the UMLActivityNode object parsed from the JSON object
     */
    private static UMLActivityNode parseActivityNode(String activityNodeType, JsonObject activityNodeJson) {
        UMLActivityNodeType nodeType = UMLActivityNodeType.valueOf(activityNodeType);
        String jsonElementId = activityNodeJson.get(ELEMENT_ID).getAsString();
        String nodeName = activityNodeJson.get(ELEMENT_NAME).getAsString();
        return new UMLActivityNode(nodeName, jsonElementId, nodeType);
    }

    /**
     * Parses the given JSON representation of a UML activity to a UMLActivity Java object.
     *
     * @param activityJson the JSON object containing the activity
     * @return the UMLActivity object parsed from the JSON object
     */
    private static UMLActivity parseActivity(JsonObject activityJson) {
        String jsonElementId = activityJson.get(ELEMENT_ID).getAsString();
        String activityName = activityJson.get(ELEMENT_NAME).getAsString();
        return new UMLActivity(activityName, new ArrayList<>(), jsonElementId);
    }

    /**
     * Parses the given JSON representation of a UML control flow to a UMLControlFlow Java object.
     *
     * @param controlFlowJson the JSON object containing the control flow
     * @param activityElementMap a map containing all activity elements of the corresponding activity diagram, necessary for assigning source and target element of the control flow
     * @return the UMLControlFlow object parsed from the JSON object
     * @throws IOException when no activity elements could be found in the activityElementMap for the source and target ID in the JSON object
     */
    private static UMLControlFlow parseControlFlow(JsonObject controlFlowJson, Map<String, UMLActivityElement> activityElementMap) throws IOException {
        UMLActivityElement source = findElement(controlFlowJson, activityElementMap, RELATIONSHIP_SOURCE);
        UMLActivityElement target = findElement(controlFlowJson, activityElementMap, RELATIONSHIP_TARGET);

        if (source != null && target != null) {
            return new UMLControlFlow(source, target, controlFlowJson.get(ELEMENT_ID).getAsString());
        }
        else {
            throw new IOException("Control flow source or target not part of model!");
        }
    }

    /**
     * Create a petri net from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * petri net containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a petri net containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static PetriNet buildPetriNetFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<PetriNetArc> arcs = new ArrayList<>();
        Map<String, PetriNetPlace> places = new HashMap<>();
        Map<String, PetriNetTransition> transitions = new HashMap<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            // elementType is never null
            switch (elementType) {
                case PetriNetPlace.PETRI_NET_PLACE_TYPE -> {
                    PetriNetPlace place = parsePetriNetPlace(jsonObject);
                    places.put(place.getJSONElementID(), place);
                    allElementsMap.put(place.getJSONElementID(), place);
                }
                case PetriNetTransition.PETRI_NET_TRANSITION_TYPE -> {
                    PetriNetTransition transition = parsePetriNetTransition(jsonObject);
                    transitions.put(transition.getJSONElementID(), transition);
                    allElementsMap.put(transition.getJSONElementID(), transition);
                }
                default -> {
                    // ignore unknown elements
                }
            }
        }

        // loop over all JSON control flow elements and create syntax tree links
        for (JsonElement rel : relationships) {
            Optional<PetriNetArc> petriNetArc = parsePetriNetArc(rel.getAsJsonObject(), allElementsMap);
            petriNetArc.ifPresent(arcs::add);
        }

        return new PetriNet(modelSubmissionId, List.copyOf(places.values()), List.copyOf(transitions.values()), arcs);
    }

    private static PetriNetPlace parsePetriNetPlace(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        String amountOfTokens = componentJson.get("amountOfTokens").getAsString();
        String capacity = componentJson.get("capacity").getAsString();
        return new PetriNetPlace(name, amountOfTokens, capacity, componentJson.get(ELEMENT_ID).getAsString());
    }

    private static PetriNetTransition parsePetriNetTransition(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new PetriNetTransition(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a PetriNetArc Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param allSyntaxTreeElements a map containing all objects of the corresponding syntax tree, necessary for assigning source and target element of the relationships
     * @return the PetriNetArc object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<PetriNetArc> parsePetriNetArc(JsonObject relationshipJson, Map<String, UMLElement> allSyntaxTreeElements) throws IOException {
        String multiplicity = relationshipJson.get(ELEMENT_NAME).getAsString();
        UMLElement source = findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_SOURCE);
        UMLElement target = findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }
        PetriNetArc newPetriNetArc = new PetriNetArc(multiplicity, source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newPetriNetArc);
    }

    /**
     * Create a syntax tree from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * syntax tree containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a syntax tree containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static SyntaxTree buildSyntaxTreeFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<SyntaxTreeLink> syntaxTreeLinkList = new ArrayList<>();
        Map<String, SyntaxTreeTerminal> terminalMap = new HashMap<>();
        Map<String, SyntaxTreeNonterminal> nonterminalMap = new HashMap<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            // elementType is never null
            switch (elementType) {
                case SyntaxTreeTerminal.SYNTAX_TREE_TERMINAL_TYPE -> {
                    SyntaxTreeTerminal terminal = parseTerminal(jsonObject);
                    terminalMap.put(terminal.getJSONElementID(), terminal);
                    allElementsMap.put(terminal.getJSONElementID(), terminal);
                }
                case SyntaxTreeNonterminal.SYNTAX_TREE_NONTERMINAL_TYPE -> {
                    SyntaxTreeNonterminal nonterminal = parseNonterminal(jsonObject);
                    nonterminalMap.put(nonterminal.getJSONElementID(), nonterminal);
                    allElementsMap.put(nonterminal.getJSONElementID(), nonterminal);
                }
                default -> {
                    // ignore unknown elements
                }
            }
        }

        // loop over all JSON control flow elements and create syntax tree links
        for (JsonElement rel : relationships) {
            Optional<SyntaxTreeLink> syntaxTreeLink = parseSyntaxTreeLink(rel.getAsJsonObject(), allElementsMap);
            syntaxTreeLink.ifPresent(syntaxTreeLinkList::add);
        }

        return new SyntaxTree(modelSubmissionId, List.copyOf(nonterminalMap.values()), List.copyOf(terminalMap.values()), syntaxTreeLinkList);
    }

    private static SyntaxTreeTerminal parseTerminal(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new SyntaxTreeTerminal(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    private static SyntaxTreeNonterminal parseNonterminal(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new SyntaxTreeNonterminal(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a SyntaxTreeLink Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param allSyntaxTreeElements a map containing all objects of the corresponding syntax tree, necessary for assigning source and target element of the relationships
     * @return the SyntaxTreeLink object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<SyntaxTreeLink> parseSyntaxTreeLink(JsonObject relationshipJson, Map<String, UMLElement> allSyntaxTreeElements) throws IOException {
        UMLElement source = findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_SOURCE);
        UMLElement target = findElement(relationshipJson, allSyntaxTreeElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }
        SyntaxTreeLink newSyntaxTreeLink = new SyntaxTreeLink(source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newSyntaxTreeLink);
    }

    /**
     * Create a flowchart from the model and relationship elements given as JSON arrays. It parses the JSON objects to corresponding Java objects and creates a
     * flowchart containing these UML model elements.
     *
     * @param modelElements the model elements as JSON array
     * @param relationships the relationship elements as JSON array
     * @param modelSubmissionId the ID of the corresponding modeling submission
     * @return a flowchart containing the parsed model elements and relationships
     * @throws IOException when no corresponding model elements could be found for the source and target IDs in the relationship JSON objects
     */
    private static Flowchart buildFlowchartFromJSON(JsonArray modelElements, JsonArray relationships, long modelSubmissionId) throws IOException {
        List<FlowchartFlowline> flowchartFlowlineList = new ArrayList<>();
        Map<String, FlowchartTerminal> terminalMap = new HashMap<>();
        Map<String, FlowchartDecision> decisionMap = new HashMap<>();
        Map<String, FlowchartProcess> processMap = new HashMap<>();
        Map<String, FlowchartInputOutput> inputOutputMap = new HashMap<>();
        Map<String, FlowchartFunctionCall> functionCallMap = new HashMap<>();
        Map<String, UMLElement> allElementsMap = new HashMap<>();

        // loop over all JSON elements and create the UML objects
        for (JsonElement jsonElement : modelElements) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String elementType = jsonObject.get(ELEMENT_TYPE).getAsString();
            // elementType is never null
            switch (elementType) {
                case FlowchartTerminal.FLOWCHART_TERMINAL_TYPE -> {
                    FlowchartTerminal terminal = parseFlowchartTerminal(jsonObject);
                    terminalMap.put(terminal.getJSONElementID(), terminal);
                    allElementsMap.put(terminal.getJSONElementID(), terminal);
                }
                case FlowchartDecision.FLOWCHART_DECISION_TYPE -> {
                    FlowchartDecision decision = parseFlowchartDecision(jsonObject);
                    decisionMap.put(decision.getJSONElementID(), decision);
                    allElementsMap.put(decision.getJSONElementID(), decision);
                }
                case FlowchartProcess.FLOWCHART_PROCESS_TYPE -> {
                    FlowchartProcess process = parseFlowchartProcess(jsonObject);
                    processMap.put(process.getJSONElementID(), process);
                    allElementsMap.put(process.getJSONElementID(), process);
                }
                case FlowchartInputOutput.FLOWCHART_INPUT_OUTPUT_TYPE -> {
                    FlowchartInputOutput inputOutput = parseFlowchartInputOutput(jsonObject);
                    inputOutputMap.put(inputOutput.getJSONElementID(), inputOutput);
                    allElementsMap.put(inputOutput.getJSONElementID(), inputOutput);
                }
                case FlowchartFunctionCall.FLOWCHART_FUNCTION_CALL_TYPE -> {
                    FlowchartFunctionCall functionCall = parseFlowchartFunctionCall(jsonObject);
                    functionCallMap.put(functionCall.getJSONElementID(), functionCall);
                    allElementsMap.put(functionCall.getJSONElementID(), functionCall);
                }
                default -> {
                    // ignore unknown elements
                }
            }
        }

        // loop over all JSON control flow elements and create syntax tree links
        for (JsonElement rel : relationships) {
            Optional<FlowchartFlowline> flowchartFlowline = parseFlowchartFlowline(rel.getAsJsonObject(), allElementsMap);
            flowchartFlowline.ifPresent(flowchartFlowlineList::add);
        }

        return new Flowchart(modelSubmissionId, List.copyOf(terminalMap.values()), List.copyOf(processMap.values()), List.copyOf(decisionMap.values()),
                List.copyOf(inputOutputMap.values()), List.copyOf(functionCallMap.values()), flowchartFlowlineList);
    }

    /**
     * Parses the given JSON representation of a Flowchart Terminal to a FlowchartTerminal Java object.
     *
     * @param componentJson the JSON object containing the terminal
     * @return the FlowchartTerminal object parsed from the JSON object
     */
    private static FlowchartTerminal parseFlowchartTerminal(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartTerminal(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Process to a FlowchartProcess Java object.
     *
     * @param componentJson the JSON object containing the process
     * @return the FlowchartProcess object parsed from the JSON object
     */
    private static FlowchartProcess parseFlowchartProcess(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartProcess(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Decision to a FlowchartDecision Java object.
     *
     * @param componentJson the JSON object containing the decision
     * @return the FlowchartDecision object parsed from the JSON object
     */
    private static FlowchartDecision parseFlowchartDecision(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartDecision(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Function Call to a FlowchartFunctionCall Java object.
     *
     * @param componentJson the JSON object containing the function call
     * @return the FlowchartFunctionCall object parsed from the JSON object
     */
    private static FlowchartFunctionCall parseFlowchartFunctionCall(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartFunctionCall(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a Flowchart Input Output to a FlowchartInputOutput Java object.
     *
     * @param componentJson the JSON object containing the input output
     * @return the FlowchartInputOutput object parsed from the JSON object
     */
    private static FlowchartInputOutput parseFlowchartInputOutput(JsonObject componentJson) {
        String name = componentJson.get(ELEMENT_NAME).getAsString();
        return new FlowchartInputOutput(name, componentJson.get(ELEMENT_ID).getAsString());
    }

    /**
     * Parses the given JSON representation of a UML relationship to a FlowchartFlowline Java object.
     *
     * @param relationshipJson the JSON object containing the relationship
     * @param allFlowchartElements a map containing all objects of the corresponding flowchart, necessary for assigning source and target element of the relationships
     * @return the FlowchartFlowline object parsed from the JSON object
     * @throws IOException when no class could be found in the classMap for the source and target ID in the JSON object
     */
    private static Optional<FlowchartFlowline> parseFlowchartFlowline(JsonObject relationshipJson, Map<String, UMLElement> allFlowchartElements) throws IOException {
        UMLElement source = findElement(relationshipJson, allFlowchartElements, RELATIONSHIP_SOURCE);
        UMLElement target = findElement(relationshipJson, allFlowchartElements, RELATIONSHIP_TARGET);

        if (source == null || target == null) {
            throw new IOException("Relationship source or target not part of model!");
        }
        FlowchartFlowline newFlowchartFlowline = new FlowchartFlowline(source, target, relationshipJson.get(ELEMENT_ID).getAsString());
        return Optional.of(newFlowchartFlowline);
    }

    /**
     * Create a map from the elements of the given JSON array. Every entry contains the ID of the element as key and the corresponding JSON element as value.
     *
     * @param elements a JSON array of elements from which the map should be created
     * @return a map that maps elementId -> element
     */
    // TODO: why is this done more than once?
    private static Map<String, JsonObject> generateJsonElementMap(JsonArray elements) {
        Map<String, JsonObject> jsonElementMap = new HashMap<>();
        elements.forEach(element -> jsonElementMap.put(element.getAsJsonObject().get("id").getAsString(), element.getAsJsonObject()));
        return jsonElementMap;
    }
}
