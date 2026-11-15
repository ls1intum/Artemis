package de.tum.cit.aet.artemis.programming.service.structureoraclegenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

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

    /**
     * This method is used to serialize the string representations of each modifier into a list.
     *
     * @param modifiers  A collection of modifiers that needs to get serialized.
     * @param javaMember The model of the {@link java.lang.reflect.Member} for which all modifiers should get serialized
     * @return The list containing the string representations of the modifiers.
     */
    static List<String> serializeModifiers(Set<String> modifiers, JavaMember javaMember) {
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
        return new ArrayList<>(modifiers);
    }

    /**
     * This method is used to serialize the simple names of the annotations into a list, leaving out the {@code @Override} annotation.
     *
     * @param annotations The annotations of the java member (e.g. Override, Inject, etc.)
     * @return The list containing the simple names of the annotations.
     */
    static List<String> serializeAnnotations(List<JavaAnnotation> annotations) {
        filterAnnotations(annotations);
        List<String> annotationNames = new ArrayList<>();
        annotations.forEach(annotation -> annotationNames.add(annotation.getType().getSimpleName()));
        return annotationNames;
    }

    /**
     * This method serializes the annotations of a java member only when the member declares at least one annotation before the {@code @Override} annotation is filtered out,
     * so that a member carrying only {@code @Override} still yields an empty list.
     *
     * @param annotations The annotations of the java member (e.g. Override, Inject, etc.)
     * @return The list containing the simple names of the annotations, or null when the member declares no annotation at all
     */
    @Nullable
    static List<String> serializeAnnotationsIfPresent(List<JavaAnnotation> annotations) {
        return annotations.isEmpty() ? null : serializeAnnotations(annotations);
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
     * This method is used to serialize the string representations of each parameter into a list.
     *
     * @param parameters A collection of parameters that needs to get serialized.
     * @return The list containing the string representations of the parameter types.
     */
    static List<String> serializeParameters(List<JavaParameter> parameters) {
        List<String> parameterTypes = new ArrayList<>();
        parameters.forEach(parameter -> parameterTypes.add(parameter.getType().getValue()));
        return parameterTypes;
    }
}
