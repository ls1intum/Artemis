package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.qdox.model.*;

/**
 * This class contains helper methods for serializing information on structural elements that we deal with repeatedly throughout the other serializers in order to avoid code
 * repetition.
 */
class SerializerUtil {

    /**
     * This method is used to serialize the string representations of each modifier into a JSON array.
     *
     * @param modifiers A collection of modifiers that needs to get serialized.
     * @param javaMember The model of the {@link java.lang.reflect.Member} for which all modifiers should get serialized
     * @return The JSON array containing the string representations of the modifiers.
     */
    static JsonArray serializeModifiers(Set<String> modifiers, JavaMember javaMember) {
        JsonArray modifiersArray = new JsonArray();
        if (javaMember.getDeclaringClass().isInterface()) {
            // Add some additional modifiers that are not reported by the qdox framework
            if (javaMember instanceof JavaMethod method) {
                if (method.isDefault() || method.isStatic()) {
                    // default and static interface methods are always public
                    modifiers.add("public");
                }
                else if (!method.isPrivate()) {
                    // "normal" interface methods are always public and abstract
                    modifiers.add("public");
                    modifiers.add("abstract");
                }
            }
            else if (javaMember instanceof JavaField) {
                // interface attributes are always public, static and final
                modifiers.add("public");
                modifiers.add("static");
                modifiers.add("final");
            }
        }
        for (String modifier : modifiers) {
            modifiersArray.add(modifier);
        }
        return modifiersArray;
    }

    /**
     * This method is used to serialize the string representations of each modifier into a JSON array.
     *
     * @param annotations The annotations of the java member (e.g. Override, Inject, etc.)
     * @return The JSON array containing the string representations of the modifiers.
     */
    static JsonArray serializeAnnotations(List<JavaAnnotation> annotations) {
        filterAnnotations(annotations);
        JsonArray annotationsArray = new JsonArray();
        for (JavaAnnotation annotation : annotations) {
            annotationsArray.add(annotation.getType().getSimpleName());
        }
        return annotationsArray;
    }

    /**
     * This method removes the @Override annotation from the list
     * since it cannot be tested against later.
     *
     * @param annotations List of annotations to filter
     */
    private static void filterAnnotations(List<JavaAnnotation> annotations) {
        annotations.removeIf(javaAnnotation -> javaAnnotation.getType().isA(Override.class.getName()));
    }

    /**
     * This method is used to serialize the string representations of each parameter into a JSON array.
     *
     * @param parameters A collection of modifiers that needs to get serialized.
     * @return The JSON array containing the string representations of the parameter types.
     */
    static JsonArray serializeParameters(List<JavaParameter> parameters) {
        JsonArray parametersArray = new JsonArray();
        for (JavaParameter parameter : parameters) {
            parametersArray.add(parameter.getType().getValue());
        }

        return parametersArray;
    }

    /**
     * creates the json object for the serialization and inserts the name and the modifiers
     *
     * @param name The name property of the new JSON object
     * @param javaMember The model for the {@link java.lang.reflect.Member} for which all modifiers should get serialized
     * @param modifiers A collection of modifiers that need to get serialized
     * @param annotations A collection of annotations that need to get serialized
     * @return A new JSON object containing all serialized modifiers under the {@code "modifiers"} key and the name of
     *  the object under the {@code "name"} key
     */
    static JsonObject createJsonObject(String name, Set<String> modifiers, JavaMember javaMember, List<JavaAnnotation> annotations) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", name);
        jsonObject.add("modifiers", serializeModifiers(modifiers, javaMember));
        if (!annotations.isEmpty()) {
            jsonObject.add("annotations", serializeAnnotations(annotations));
        }
        return jsonObject;
    }
}
