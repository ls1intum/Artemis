package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;

/**
 * This class is used to serialize the elements that are unique to classes, e.g. attributes and constructors.
 */
public class ClassesDiffSerializer {

    private ClassesDiff classesDiff;

    public ClassesDiffSerializer(ClassesDiff classesDiff) {
        this.classesDiff = classesDiff;
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

        for(CtField<?> attribute : classesDiff.attributesDiff) {
            JsonObject attributeJSON = SerializerUtil.createJsonObject(attribute.getSimpleName(), attribute.getModifiers());
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

        for(CtConstructor<?> constructor : classesDiff.constructorsDiff) {
            JsonObject constructorJSON = new JsonObject();

            if(!constructor.getModifiers().isEmpty()) {
                constructorJSON.add("modifiers", SerializerUtil.serializeModifiers(constructor.getModifiers()));
            }
            if(!constructor.getParameters().isEmpty()) {
                constructorJSON.add("parameters", SerializerUtil.serializeParameters(constructor.getParameters(), constructor.getDeclaringType().isEnum()));
            }
            constructorsJSON.add(constructorJSON);
        }
        return constructorsJSON;
    }
}
