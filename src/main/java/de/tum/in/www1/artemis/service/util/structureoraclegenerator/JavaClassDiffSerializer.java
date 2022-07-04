package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.util.HashSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.qdox.model.*;

/**
 * This class is used to serialize the elements that are generally defined in types, e.g. methods and various properties of the types. These properties are defined as the hierarchy
 * of the types.
 */
class JavaClassDiffSerializer {

    private JavaClassDiff javaClassDiff;

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
    JsonObject serializeClassProperties() {
        JsonObject classJSON = new JsonObject();

        classJSON.addProperty("name", javaClassDiff.getName());
        classJSON.addProperty("package", javaClassDiff.getPackageName());

        if (javaClassDiff.isInterfaceDifferent) {
            classJSON.addProperty("isInterface", true);
        }
        if (javaClassDiff.isEnumDifferent) {
            classJSON.addProperty("isEnum", true);
        }
        if (javaClassDiff.isAbstractDifferent) {
            classJSON.addProperty("isAbstract", true);
        }
        if (!javaClassDiff.superClassNameDiff.isEmpty()) {
            classJSON.addProperty("superclass", javaClassDiff.superClassNameDiff);
        }
        if (!javaClassDiff.superInterfacesDiff.isEmpty()) {
            JsonArray superInterfaces = new JsonArray();

            for (JavaClass superInterface : javaClassDiff.superInterfacesDiff) {
                superInterfaces.add(superInterface.getSimpleName());
            }
            classJSON.add("interfaces", superInterfaces);
        }
        if (!javaClassDiff.annotationsDiff.isEmpty()) {
            JsonArray annotations = new JsonArray();

            for (JavaAnnotation annotation : javaClassDiff.annotationsDiff) {
                annotations.add(annotation.getType().getSimpleName());
            }
            classJSON.add("annotations", annotations);
        }
        return classJSON;
    }

    /**
     * This method is used to serialize the attributes of a class into a JSON array containing the following information for each attribute defined in the classes packed into a
     * JSON object: - Name - Modifiers (if any) - Type
     *
     * @return The JSON array consisting of JSON objects representation for each attribute defined in the classes diff.
     */
    JsonArray serializeAttributes() {
        JsonArray attributesJSON = new JsonArray();

        for (JavaField attribute : javaClassDiff.attributesDiff) {
            JsonObject attributeJSON = SerializerUtil.createJsonObject(attribute.getName(), new HashSet<>(attribute.getModifiers()), attribute, attribute.getAnnotations());
            attributeJSON.addProperty("type", attribute.getType().getValue());
            attributesJSON.add(attributeJSON);
        }

        return attributesJSON;
    }

    /**
     * This method is used to serialize the enums of a class into a JSON array containing each enum value:
     *
     * @return The JSON array consisting of JSON objects representation for each enum defined in the classes diff.
     */
    JsonArray serializeEnums() {
        JsonArray enumsJSON = new JsonArray();

        for (JavaField javaEnum : javaClassDiff.enumsDiff) {
            enumsJSON.add(javaEnum.getName());
        }

        return enumsJSON;
    }

    /**
     * This method is used to serialize the constructors of a class into a JSON array containing the following information for each constructor defined in the classes packed into a
     * JSON object: - Modifiers (if any) - Parameter types (if any)
     *
     * @return The JSON array consisting of JSON objects representation for each constructor defined in the classes diff.
     */
    JsonArray serializeConstructors() {
        JsonArray constructorsJSON = new JsonArray();

        for (JavaConstructor constructor : javaClassDiff.constructorsDiff) {
            JsonObject constructorJSON = new JsonObject();

            if (!constructor.getModifiers().isEmpty()) {
                constructorJSON.add("modifiers", SerializerUtil.serializeModifiers(new HashSet<>(constructor.getModifiers()), constructor));
            }
            if (!constructor.getParameters().isEmpty()) {
                constructorJSON.add("parameters", SerializerUtil.serializeParameters(constructor.getParameters()));
            }
            if (!constructor.getAnnotations().isEmpty()) {
                constructorJSON.add("annotations", SerializerUtil.serializeAnnotations(constructor.getAnnotations()));
            }
            constructorsJSON.add(constructorJSON);
        }
        return constructorsJSON;
    }

    /**
     * This method is used to serialize the methods of a type into a JSON array containing the following information for each method defined in the classes packed into a JSON
     * object: - Name - Modifiers (if any) - Parameter types (if any) - Return type
     *
     * @return The JSON array consisting of JSON objects representation for each method defined in the types diff.
     */
    JsonArray serializeMethods() {
        JsonArray methodsJSON = new JsonArray();

        for (JavaMethod method : javaClassDiff.methodsDiff) {
            JsonObject methodJSON = SerializerUtil.createJsonObject(method.getName(), new HashSet<>(method.getModifiers()), method, method.getAnnotations());
            if (!method.getParameters().isEmpty()) {
                methodJSON.add("parameters", SerializerUtil.serializeParameters(method.getParameters()));
            }
            methodJSON.addProperty("returnType", method.getReturnType().getValue());
            methodsJSON.add(methodJSON);
        }
        return methodsJSON;
    }
}
