package de.tum.cit.aet.artemis.shared.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tngtech.archunit.core.domain.JavaField;

/**
 * Holds every type stored in a json column to reading rows that were written by an older version of itself.
 * <p>
 * A json column is written by the type as it looks today and read back by the type as it looks whenever the row is next
 * read, which can be years and several releases later. Hibernate reads such a column with its own Jackson mapper, and
 * that mapper rejects a property the type no longer declares. So removing a field is enough to make every row written
 * before that release unreadable, and no later release can repair them, because the rows already say what they say.
 * <p>
 * That is not theory: the exercise version snapshot lost {@code allowFeedbackRequests} when Athena's feedback
 * configuration moved to the course, which stopped those exercises from ever being versioned again and answered the
 * version history with 500. Nothing failed at the time of the removal, and nothing could have: only a row older than
 * the change shows it.
 * <p>
 * The protection is {@code @JsonIgnoreProperties(ignoreUnknown = true)} on the stored type. It costs nothing elsewhere,
 * since the application mapper disables {@code FAIL_ON_UNKNOWN_PROPERTIES} anyway, and this test requires it of the
 * whole tree a column stores rather than of its root alone, because a field can be removed at any depth.
 */
class JsonColumnCompatibilityArchitectureTest extends AbstractArchitectureTest {

    @Test
    void everyTypeStoredInAJsonColumnToleratesPropertiesItNoLongerDeclares() {
        Set<Class<?>> stored = new LinkedHashSet<>();
        jsonColumnTypes().forEach(type -> collectStoredTypes(type, stored));

        List<String> unprotected = stored.stream().filter(storedType -> !toleratesUnknownProperties(storedType)).map(Class::getName).sorted().toList();

        assertThat(unprotected).as("""
                Every type stored in a json column has to be annotated @JsonIgnoreProperties(ignoreUnknown = true), \
                including the types nested inside it. Without it, removing a field from the type makes every row \
                written before that release unreadable, which surfaces long after the change and cannot be repaired \
                afterwards.""").isEmpty();
    }

    /**
     * The declared types of all fields mapped to a json column.
     *
     * @return the generic type of every field annotated with {@code @JdbcTypeCode(SqlTypes.JSON)}
     */
    private static Stream<Type> jsonColumnTypes() {
        return productionClasses.stream().flatMap(javaClass -> javaClass.getFields().stream()).filter(field -> field.isAnnotatedWith(JdbcTypeCode.class))
                .filter(field -> field.getAnnotationOfType(JdbcTypeCode.class).value() == SqlTypes.JSON).map(JavaField::reflect).map(Field::getGenericType);
    }

    /**
     * Collects every Artemis type that ends up inside the json, following record components and fields.
     * <p>
     * Enums and interfaces are left out: an enum carries no properties, and an interface is stored through whichever
     * implementation is written, which this walk cannot see from the declaration alone.
     *
     * @param type      the declared type to inspect
     * @param collected the set the walk adds to, which also stops it from following a cycle twice
     */
    private static void collectStoredTypes(Type type, Set<Class<?>> collected) {
        rawTypesOf(type).toList().forEach(candidate -> {
            if (!candidate.getName().startsWith(ARTEMIS_PACKAGE) || candidate.isEnum() || candidate.isInterface() || !collected.add(candidate)) {
                return;
            }
            componentTypesOf(candidate).toList().forEach(component -> collectStoredTypes(component, collected));
        });
    }

    /**
     * The types a stored type is made of: the components of a record, or the instance fields of a class.
     *
     * @param storedType the type being stored
     * @return the generic types it holds
     */
    private static Stream<Type> componentTypesOf(Class<?> storedType) {
        if (storedType.isRecord()) {
            return Arrays.stream(storedType.getRecordComponents()).map(RecordComponent::getGenericType);
        }
        return Arrays.stream(storedType.getDeclaredFields()).filter(field -> !Modifier.isStatic(field.getModifiers())).filter(field -> !Modifier.isTransient(field.getModifiers()))
                .map(Field::getGenericType);
    }

    /**
     * Unwraps a declared type to the classes json is actually written for, so that the element of a collection and the
     * key and value of a map are followed rather than the container.
     *
     * @param type the declared type
     * @return the classes it resolves to
     */
    private static Stream<Class<?>> rawTypesOf(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz.isArray() ? rawTypesOf(clazz.getComponentType()) : Stream.of(clazz);
            case ParameterizedType parameterized -> Stream.concat(rawTypesOf(parameterized.getRawType()),
                    Arrays.stream(parameterized.getActualTypeArguments()).flatMap(JsonColumnCompatibilityArchitectureTest::rawTypesOf));
            case GenericArrayType array -> rawTypesOf(array.getGenericComponentType());
            case WildcardType wildcard -> Arrays.stream(wildcard.getUpperBounds()).flatMap(JsonColumnCompatibilityArchitectureTest::rawTypesOf);
            case TypeVariable<?> variable -> Arrays.stream(variable.getBounds()).flatMap(JsonColumnCompatibilityArchitectureTest::rawTypesOf);
            default -> Stream.empty();
        };
    }

    /**
     * Whether a type reads a row that carries a property the type no longer declares.
     *
     * @param storedType the type being stored
     * @return true if it is annotated to ignore unknown properties
     */
    private static boolean toleratesUnknownProperties(Class<?> storedType) {
        JsonIgnoreProperties annotation = storedType.getAnnotation(JsonIgnoreProperties.class);
        return annotation != null && annotation.ignoreUnknown();
    }
}
