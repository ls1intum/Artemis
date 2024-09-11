package de.tum.cit.aet.artemis.service.util.structureoraclegenerator;

import java.util.HashSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thoughtworks.qdox.model.JavaAnnotatedElement;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaConstructor;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;

/**
 * This class is used to serialize the elements that are generally defined in types, e.g. methods and various properties of the types. These properties are defined as the hierarchy
 * of the types.
 */
class JavaClassDiffSerializer {

    private final JavaClassDiff javaClassDiff;

    private static final ObjectMapper mapper = new ObjectMapper();

    JavaClassDiffSerializer(JavaClassDiff javaClassDiff) {
        this.javaClassDiff = javaClassDiff;
    }

    /**
     * This method is used to serialize the class properties of each type defined in the types diff into a JSON object containing the following information:
     * - Name of the type
     * - Package of the type
     * - Interface stereotype
     * - Enum stereotype
     * - Abstract stereotype
     * - Superclass (if any)
     * - Super interfaces (if any)
     * - Annotations (if any)
     *
     * @return The JSON object consisting of JSON objects representation for the wanted hierarchy properties of a type defined in the types diff.
     */
    ObjectNode serializeClassProperties() {
        ObjectNode classJSON = mapper.createObjectNode();

        // Directly setting values based on class properties
        classJSON.put("name", javaClassDiff.getName());
        classJSON.put("package", javaClassDiff.getPackageName());
        classJSON.put("isInterface", javaClassDiff.isInterfaceDifferent);
        classJSON.put("isEnum", javaClassDiff.isEnumDifferent);
        classJSON.put("isAbstract", javaClassDiff.isAbstractDifferent);

        // Adding superclass name if present
        if (!javaClassDiff.superClassNameDiff.isEmpty()) {
            classJSON.put("superclass", javaClassDiff.superClassNameDiff);
        }

        // Serializing interfaces
        if (!javaClassDiff.superInterfacesDiff.isEmpty()) {
            classJSON.set("interfaces", serializeSuperInterfaces(javaClassDiff.superInterfacesDiff));
        }

        // Serializing annotations
        if (!javaClassDiff.annotationsDiff.isEmpty()) {
            classJSON.set("annotations", serializeAnnotations(javaClassDiff.annotationsDiff));
        }

        return classJSON;
    }

    private ArrayNode serializeSuperInterfaces(Iterable<JavaClass> superInterfaces) {
        ArrayNode superInterfacesNode = mapper.createArrayNode();
        superInterfaces.forEach(superInterface -> superInterfacesNode.add(superInterface.getSimpleName()));
        return superInterfacesNode;
    }

    private ArrayNode serializeAnnotations(Iterable<JavaAnnotation> annotations) {
        ArrayNode annotationsNode = mapper.createArrayNode();
        annotations.forEach(annotation -> annotationsNode.add(annotation.getType().getSimpleName()));
        return annotationsNode;
    }

    /**
     * This method is used to serialize the attributes of a class into a JSON array containing the following information for each attribute defined in the classes packed into a
     * JSON object: - Name - Modifiers (if any) - Type
     *
     * @return The JSON array consisting of JSON objects representation for each attribute defined in the classes diff.
     */
    ArrayNode serializeAttributes() {
        ArrayNode attributesJSON = mapper.createArrayNode();

        javaClassDiff.attributesDiff.stream().filter(attribute -> !JavaClassDiffSerializer.isElementToIgnore(attribute))
                .forEach(attribute -> attributesJSON.add(createAttributeJson(attribute)));

        return attributesJSON;
    }

    private ObjectNode createAttributeJson(JavaField attribute) {
        ObjectNode attributeJSON = SerializerUtil.createJsonObject(attribute.getName(), new HashSet<>(attribute.getModifiers()), attribute, attribute.getAnnotations());
        attributeJSON.put("type", attribute.getType().getValue());
        return attributeJSON;
    }

    /**
     * This method is used to serialize the enums of a class into a JSON array containing each enum value:
     *
     * @return The JSON array consisting of JSON objects representation for each enum defined in the classes diff.
     */
    ArrayNode serializeEnums() {
        ArrayNode enumsJSON = mapper.createArrayNode();

        javaClassDiff.enumsDiff.stream().filter(enumField -> !isElementToIgnore(enumField)).forEach(enumField -> enumsJSON.add(enumField.getName()));

        return enumsJSON;
    }

    /**
     * This method is used to serialize the constructors of a class into a JSON array containing the following information for each constructor defined in the classes packed into a
     * JSON object: - Modifiers (if any) - Parameter types (if any)
     *
     * @return The JSON array consisting of JSON objects representation for each constructor defined in the classes diff.
     */
    ArrayNode serializeConstructors() {
        ArrayNode constructorsJSON = mapper.createArrayNode();

        javaClassDiff.constructorsDiff.stream().filter(constructor -> !isElementToIgnore(constructor))
                .forEach(constructor -> constructorsJSON.add(serializeConstructor(constructor)));

        return constructorsJSON;
    }

    private ObjectNode serializeConstructor(JavaConstructor constructor) {
        ObjectNode constructorJSON = mapper.createObjectNode();

        // No need to check for isEmpty before adding; let the serializer utility methods decide how to handle empty collections
        constructorJSON.set("modifiers", SerializerUtil.serializeModifiers(new HashSet<>(constructor.getModifiers()), constructor));
        constructorJSON.set("parameters", SerializerUtil.serializeParameters(constructor.getParameters()));
        constructorJSON.set("annotations", SerializerUtil.serializeAnnotations(constructor.getAnnotations()));

        return constructorJSON;
    }

    /**
     * This method is used to serialize the methods of a type into a JSON array containing the following information for each method defined in the classes packed into a JSON
     * object: - Name - Modifiers (if any) - Parameter types (if any) - Return type
     *
     * @return The JSON array consisting of JSON objects representation for each method defined in the types diff.
     */
    ArrayNode serializeMethods() {
        ArrayNode methodsJSON = mapper.createArrayNode();

        javaClassDiff.methodsDiff.stream().filter(method -> !isElementToIgnore(method)).forEach(method -> methodsJSON.add(createMethodJson(method)));

        return methodsJSON;
    }

    private ObjectNode createMethodJson(JavaMethod method) {
        ObjectNode methodJSON = SerializerUtil.createJsonObject(method.getName(), new HashSet<>(method.getModifiers()), method, method.getAnnotations());

        // No need to check for isEmpty before adding; let the serializer utility methods decide how to handle empty collections
        methodJSON.set("parameters", SerializerUtil.serializeParameters(method.getParameters()));
        methodJSON.put("returnType", method.getReturnType().getValue());

        return methodJSON;
    }

    public static boolean isElementToIgnore(JavaAnnotatedElement element) {
        return element.getTagByName("oracleIgnore") != null;
    }
}
