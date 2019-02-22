package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import org.json.JSONArray;
import org.json.JSONObject;
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
    public JSONArray serializeAttributes() {
        JSONArray attributesJSON = new JSONArray();

        for(CtField<?> attribute : classesDiff.attributes) {
            JSONObject attributeJSON = SerializerUtil.createJsonObject(attribute.getSimpleName(), attribute.getModifiers());
            attributeJSON.put("type", attribute.getType().getSimpleName());
            attributesJSON.put(attributeJSON);
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
    public JSONArray serializeConstructors() {
        JSONArray constructorsJSON = new JSONArray();

        for(CtConstructor<?> constructor : classesDiff.constructors) {
            JSONObject constructorJSON = new JSONObject();

            if(!constructor.getModifiers().isEmpty()) {
                constructorJSON.put("modifiers", SerializerUtil.serializeModifiers(constructor.getModifiers()));
            }
            if(!constructor.getParameters().isEmpty()) {
                constructorJSON.put("parameters", SerializerUtil.serializeParameters(constructor.getParameters(), constructor.getDeclaringType().isEnum()));
            }
            constructorsJSON.put(constructorJSON);
        }
        return constructorsJSON;
    }
}
