package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.qdox.model.*;

import java.util.HashSet;

/**
 * This class is used to serialize the elements that are generally defined in types, e.g. methods and various properties
 * of the types. These properties are defined as the hierarchy of the types.
 */
public class TypesDiffSerializer {

    private TypesDiff typesDiff;

    public TypesDiffSerializer(TypesDiff typesDiff) {
        this.typesDiff = typesDiff;
    }

    /**
     * This method is used to serialize the hierarchy properties of each type defined in the types diff
     * into a JSON object containing the following information:
     * - Name of the type
     * - Package of the type
     * - Interface stereotype
     * - Enum stereotype
     * - Abstract stereotype
     * - Superclass (if any)
     * - Super interfaces (if any)
     * @return The JSON object consisting of JSON objects representation for the wanted hierarchy properties of a type
     * defined in the types diff.
     */
    public JsonObject serializeHierarchy() {
        JsonObject hierarchyJSON = new JsonObject();

        hierarchyJSON.addProperty("name", typesDiff.name);
        hierarchyJSON.addProperty("package", typesDiff.packageName);

        if(typesDiff.isInterface) {
            hierarchyJSON.addProperty("isInterface", true);
        }
        if(typesDiff.isEnumDifferent) {
            hierarchyJSON.addProperty("isEnum", true);
        }
        if(typesDiff.isAbstractDifferent) {
            hierarchyJSON.addProperty("isAbstract", true);
        }
        if(!typesDiff.superClassName.isEmpty()) {
            hierarchyJSON.addProperty("superclass", typesDiff.superClassName);
        }
        if(typesDiff.superInterfaces.size() > 0) {
            JsonArray superInterfaces = new JsonArray();

            for(JavaClass superInterface : typesDiff.superInterfaces) {
                superInterfaces.add(superInterface.getSimpleName());
            }
            hierarchyJSON.add("interfaces", superInterfaces);
        }
        return hierarchyJSON;
    }

    /**
     * This method is used to serialize the attributes of a class into a JSON array containing the following information for each
     * attribute defined in the classes packed into a JSON object:
     * - Name
     * - Modifiers (if any)
     * - Type
     * @return The JSON array consisting of JSON objects representation for each attribute defined in the classes diff.
     */
    public JsonArray serializeAttributes() {
        JsonArray attributesJSON = new JsonArray();

        for(JavaField attribute : typesDiff.attributesDiff) {
            JsonObject attributeJSON = SerializerUtil.createJsonObject(attribute.getName(), new HashSet<>(attribute.getModifiers()), attribute);
            attributeJSON.addProperty("type", attribute.getType().getSimpleName());
            attributesJSON.add(attributeJSON);
        }

        return attributesJSON;
    }

    /**
     * This method is used to serialize the constructors of a class into a JSON array containing the following information for each
     * constructor defined in the classes packed into a JSON object:
     * - Modifiers (if any)
     * - Parameter types (if any)
     * @return The JSON array consisting of JSON objects representation for each constructor defined in the classes diff.
     */
    public JsonArray serializeConstructors() {
        JsonArray constructorsJSON = new JsonArray();

        for(JavaConstructor constructor : typesDiff.constructorsDiff) {
            JsonObject constructorJSON = new JsonObject();

            if(!constructor.getModifiers().isEmpty()) {
                constructorJSON.add("modifiers", SerializerUtil.serializeModifiers(new HashSet<>(constructor.getModifiers()), constructor));
            }
            if(!constructor.getParameters().isEmpty()) {
                constructorJSON.add("parameters", SerializerUtil.serializeParameters(constructor.getParameters()));
            }
            constructorsJSON.add(constructorJSON);
        }
        return constructorsJSON;
    }

    /**
     * This method is used to serialize the methods of a type into a JSON array containing the following information for each
     * method defined in the classes packed into a JSON object:
     * - Name
     * - Modifiers (if any)
     * - Parameter types (if any)
     * - Return type
     * @return The JSON array consisting of JSON objects representation for each method defined in the types diff.
     */
    public JsonArray serializeMethods() {
        JsonArray methodsJSON = new JsonArray();

        for(JavaMethod method : typesDiff.methodsDiff) {
            JsonObject methodJSON = SerializerUtil.createJsonObject(method.getName(), new HashSet<>(method.getModifiers()), method);
            if(!method.getParameters().isEmpty()) {
                methodJSON.add("parameters", SerializerUtil.serializeParameters(method.getParameters()));
            }
            methodJSON.addProperty("returnType", method.getReturnType().getValue());
            methodsJSON.add(methodJSON);
        }
        return methodsJSON;
    }
}
