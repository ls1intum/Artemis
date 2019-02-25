package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.qdox.model.*;

import java.util.*;

/**
 * This class contains helper methods for serializing information on structural elements that we deal with repeatedly
 * throughout the other serializers in order to avoid code repetition.
 */
public class SerializerUtil {

    /**
     * This method is used to serialize the string representations of each modifier into a JSON array.
     * @param modifiers A collection of modifiers that needs to get serialized.
     * @return The JSON array containing the string representations of the modifiers.
     */
    public static JsonArray serializeModifiers(Set<String> modifiers, JavaMember javaMember) {
        JsonArray modifiersArray = new JsonArray();
        if (javaMember.getDeclaringClass().isInterface()) {
            // constructors are not possible here
            if (javaMember instanceof JavaMethod) {
                // interface methods are always public and abstract, however the qdox framework does not report this when parsing the Java source file
                modifiers.add("public");
                modifiers.add("abstract");
            }
            else if (javaMember instanceof JavaField) {
                // interface attributes are always public, static and final, however the qdox framework does not report this when parsing the Java source file
                modifiers.add("public");
                modifiers.add("static");
                modifiers.add("final");
            }
        }
        for(String modifier : modifiers) {
            modifiersArray.add(modifier);
        }
        return modifiersArray;
    }

    /**
     * This method is used to serialize the string representations of each parameter into a JSON array.
     * @param parameters A collection of modifiers that needs to get serialized.
     * @return The JSON array containing the string representations of the parameter types.
     */
    public static JsonArray serializeParameters(List<JavaParameter> parameters) {
        JsonArray parametersArray = new JsonArray();
        for(JavaParameter parameter : parameters) {
            parametersArray.add(parameter.getType().getValue());
        }

        return parametersArray;
    }

    /**
     * creates the json object for the serialization and inserts the name and the modifiers
     *
     * @param name
     * @param modifiers
     * @return
     */
    public static JsonObject createJsonObject(String name, Set<String> modifiers, JavaMember javaMember) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", name);
        if(!modifiers.isEmpty()) {
            jsonObject.add("modifiers", serializeModifiers(modifiers, javaMember));
        }
        return jsonObject;
    }
}
