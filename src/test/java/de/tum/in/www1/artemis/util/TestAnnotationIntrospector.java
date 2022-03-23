package de.tum.in.www1.artemis.util;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.util.Pair;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

import de.tum.in.www1.artemis.domain.User;

/**
 * This class may be used to disable certain Jackson annotations.
 * ONLY use this for TESTING
 *
 * Rational:
 * In Artemis we have properties that we don't expose to the client but allow the client to update them. In integration tests we simulate the client's input and use our own
 * Artemis models for this purpose.
 * But they don't get sent to the server raw but serialized in JSON format. During this process the same ignore and include rules apply as in production. This, however, is not
 * an intended behaviour as at this moment we are not in the production code but in the simulated client environment.
 * Because it's difficult and tedious to write an implementation perfectly curring away all problematic instances and leave all other ones, we only disable annotations for
 * elements where we know the issue occurs.
 *
 * Important:
 * We don't change the behaviour, we only make the behaviour more realistic as the client wouldn't remove the properties before sending them to the server.
 */
public class TestAnnotationIntrospector extends JacksonAnnotationIntrospector {

    /**
     * A blacklist of all annotations to be ignored.
     */
    private final List<Class<? extends Annotation>> blacklistedAnnotations = List.of(JsonIgnore.class, JsonProperty.class);

    /**
     * A blacklist of all field names. The first element of a pair is the class, the second one the name of the field.
     */
    private final List<Pair<Class<?>, String>> blacklistedFieldNames = List.of(Pair.of(User.class, "email"), Pair.of(User.class, "registrationNumber"));

    /**
     * Determines whether the annotated element is a match for our blacklist
     *
     * @param ann the element of question
     * @return true if matches
     */
    private boolean annotatedMatches(Annotated ann) {
        Class<?> clazz = getDeclaringClass(ann);
        if (clazz == null) {
            return false;
        }
        return blacklistedFieldNames.stream().anyMatch(pair -> pair.getFirst().equals(clazz) && (ann.getName().equalsIgnoreCase(pair.getSecond())
                || ann.getName().toLowerCase().equals("get" + pair.getSecond().toLowerCase()) || ann.getName().toLowerCase().equals("set" + pair.getSecond().toLowerCase())));
    }

    /**
     * Helper method to return the class used for matching. This method can be extended for different instances.
     * @param ann the element of question
     * @return The class if available, otherwise null
     */
    private Class<?> getDeclaringClass(Annotated ann) {
        if (ann instanceof AnnotatedMember) {
            return ((AnnotatedMember) ann).getDeclaringClass();
        }
        return null;
    }

    /**
     * This method returns the annotation value for an element. If we want to ignore an annotation we just claim it doesn't exist.
     * See the superclass implementation for more information.
     *
     * @param ann       The element of question
     * @param annoClass The annotation class of question
     * @param <A>       The annotation type of question
     * @return The value of the annotation, otherwise null
     */
    @Override
    protected <A extends Annotation> A _findAnnotation(Annotated ann, Class<A> annoClass) {
        if (annotatedMatches(ann) && blacklistedAnnotations.contains(annoClass)) {
            return null;
        }
        return super._findAnnotation(ann, annoClass);
    }

    /**
     * This method determines whether an element has an annotation or not. If we want to ignore an annotation we just claim it doesn't exist.
     * See the superclass implementation for more information.
     *
     * @param ann       The element of question
     * @param annoClass The annotation class of question
     * @param <A>       The annotation type of question
     * @return The value of the annotation, otherwise null
     */
    @Override
    protected boolean _hasAnnotation(Annotated ann, Class<? extends Annotation> annoClass) {
        return _findAnnotation(ann, annoClass) != null;
    }

    /**
     * This method determines whether an element has one of multiple annotations. If there is a match for the element we only check for the annotations minus the blacklisted ones.
     */
    @Override
    protected boolean _hasOneOf(Annotated ann, Class<? extends Annotation>[] annoClasses) {
        if (annotatedMatches(ann)) {
            List<Class<? extends Annotation>> newList = Arrays.stream(annoClasses).filter(clazz -> !blacklistedAnnotations.contains(clazz)).toList();

            Class<? extends Annotation>[] classes = new Class[newList.size()];
            for (int i = 0; i < newList.size(); i++) {
                classes[i] = newList.get(i);
            }
            annoClasses = classes;
        }

        return super._hasOneOf(ann, annoClasses);
    }
}
