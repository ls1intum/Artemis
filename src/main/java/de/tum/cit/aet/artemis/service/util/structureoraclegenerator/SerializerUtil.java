package de.tum.cit.aet.artemis.service.util.structureoraclegenerator;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMember;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaParameter;

/**
 * This class contains helper methods for serializing information on structural elements that we deal with repeatedly throughout the other serializers in order to avoid code
 * repetition.
 */
class SerializerUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * This method is used to serialize the string representations of each modifier into a JSON array.
     *
     * @param modifiers  A collection of modifiers that needs to get serialized.
     * @param javaMember The model of the {@link java.lang.reflect.Member} for which all modifiers should get serialized
     * @return The JSON array containing the string representations of the modifiers.
     */
    static ArrayNode serializeModifiers(Set<String> modifiers, JavaMember javaMember) {
        ArrayNode modifiersArray = mapper.createArrayNode();
        // Check if the class is an interface and adjust modifiers accordingly
        if (javaMember.getDeclaringClass().isInterface()) {
            if (javaMember instanceof JavaMethod method) {
                if (method.isDefault() || method.isStatic()) {
                    modifiers.add("public");
                }
                else if (!method.isPrivate()) {
                    modifiers.add("public");
                    modifiers.add("abstract");
                }
            }
            else if (javaMember instanceof JavaField) {
                modifiers.add("public");
                modifiers.add("static");
                modifiers.add("final");
            }
        }
        modifiers.forEach(modifiersArray::add);
        return modifiersArray;
    }

    /**
     * This method is used to serialize the string representations of each modifier into a JSON array.
     *
     * @param annotations The annotations of the java member (e.g. Override, Inject, etc.)
     * @return The JSON array containing the string representations of the modifiers.
     */
    static ArrayNode serializeAnnotations(List<JavaAnnotation> annotations) {
        filterAnnotations(annotations);
        ArrayNode annotationsArray = mapper.createArrayNode();
        annotations.forEach(annotation -> annotationsArray.add(annotation.getType().getSimpleName()));
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
    static ArrayNode serializeParameters(List<JavaParameter> parameters) {
        ArrayNode parametersArray = mapper.createArrayNode();
        parameters.forEach(parameter -> parametersArray.add(parameter.getType().getValue()));
        return parametersArray;
    }

    /**
     * creates the json object for the serialization and inserts the name and the modifiers
     *
     * @param name        The name property of the new JSON object
     * @param javaMember  The model for the {@link java.lang.reflect.Member} for which all modifiers should get serialized
     * @param modifiers   A collection of modifiers that need to get serialized
     * @param annotations A collection of annotations that need to get serialized
     * @return A new JSON object containing all serialized modifiers under the {@code "modifiers"} key and the name of
     *         the object under the {@code "name"} key
     */
    static ObjectNode createJsonObject(String name, Set<String> modifiers, JavaMember javaMember, List<JavaAnnotation> annotations) {
        ObjectNode jsonObject = mapper.createObjectNode();
        jsonObject.put("name", name);
        jsonObject.set("modifiers", serializeModifiers(modifiers, javaMember));
        if (!annotations.isEmpty()) {
            jsonObject.set("annotations", serializeAnnotations(annotations));
        }
        return jsonObject;
    }
}
