package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Abstract base class for module-specific entity usage architecture tests.
 * <p>
 * These tests verify that a module follows DTO best practices:
 * <ul>
 * <li>REST controllers do not return @Entity types directly (should return DTOs instead)</li>
 * <li>REST controllers do not accept @Entity types in @RequestBody/@RequestPart parameters (should accept DTOs instead)</li>
 * <li>DTO classes do not contain fields that reference @Entity types (prevents lazy wrapper anti-pattern)</li>
 * </ul>
 * <p>
 * To use this class:
 * <ol>
 * <li>Create a subclass in your module's architecture package</li>
 * <li>Implement {@link #getModulePackage()} to return the module's base package</li>
 * <li>Override {@link #getMaxEntityReturnViolations()}, {@link #getMaxEntityInputViolations()}, and optionally
 * {@link #getMaxDtoEntityFieldViolations()} with the current violation counts</li>
 * </ol>
 * <p>
 * <b>Important:</b> The violation counts should be reduced over time as DTOs are introduced.
 * The goal is to reach 0 violations for all methods. When fixing violations, update the
 * expected count to match the new (lower) number of violations.
 */
public abstract class AbstractModuleEntityUsageArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

    /**
     * Returns the EXACT number of entity return type violations this module currently has.
     * <p>
     * The test uses {@code hasSize(...)} (exact match), so the override here must equal the
     * actual violation count: too low fails as a regression, too high fails as a stale baseline
     * that's hiding an improvement. The "max" in the legacy method name predates the exact-match
     * semantics; treat it as "expected count".
     * <p>
     * <b>TODO:</b> Reduce to 0 over time as DTOs are introduced.
     *
     * @return the exact number of entity-return violations expected (0 means fully compliant)
     */
    protected abstract int getMaxEntityReturnViolations();

    /**
     * Returns the EXACT number of entity input (RequestBody/RequestPart) violations this module
     * currently has. See {@link #getMaxEntityReturnViolations()} for the exact-match semantics.
     *
     * @return the exact number of entity-input violations expected (0 means fully compliant)
     */
    protected abstract int getMaxEntityInputViolations();

    /**
     * Returns the EXACT number of DTO-entity-field violations this module currently has.
     * <p>
     * DTOs must not contain fields that reference @Entity types, as this defeats the purpose
     * of using DTOs (lazy wrapping pattern). DTOs should only contain primitive types,
     * date/time types, enums, and other DTOs. See {@link #getMaxEntityReturnViolations()} for
     * the exact-match semantics.
     *
     * @return the exact number of DTO-entity-field violations expected (0 means fully compliant)
     */
    protected int getMaxDtoEntityFieldViolations() {
        return 0; // Default: no violations allowed for new modules
    }

    /**
     * Verifies that REST controllers in this module do not return @Entity types directly.
     * <p>
     * This test checks:
     * <ul>
     * <li>Direct entity returns (e.g., {@code ResponseEntity<User>})</li>
     * <li>Collections containing entities (e.g., {@code ResponseEntity<List<User>>})</li>
     * <li>Nested generic types containing entities</li>
     * </ul>
     * <p>
     * The test allows up to {@link #getMaxEntityReturnViolations()} violations to support
     * incremental migration to DTOs. This number should be reduced to 0 over time.
     */
    @Test
    protected void restControllersMustNotReturnEntities() {
        List<String> violations = new ArrayList<>();

        var condition = new ArchCondition<JavaClass>("not return @Entity types from REST controller methods") {

            @Override
            public void check(JavaClass controllerClass, ConditionEvents events) {
                Class<?> reflectedController = controllerClass.reflect();

                for (JavaMethod archMethod : controllerClass.getMethods()) {
                    // Keep output clean (ignore inherited methods)
                    if (!archMethod.getOwner().equals(controllerClass)) {
                        continue;
                    }

                    Method reflectedMethod = findMatchingDeclaredMethod(reflectedController, archMethod);
                    if (reflectedMethod == null) {
                        // synthetic/bridge methods etc. - skip safely
                        continue;
                    }

                    // Only actual REST endpoints matter — skip private/helper methods (e.g. DTO->entity mappers)
                    // that merely happen to live in a @RestController and return an entity.
                    if (!isRestEndpointMethod(reflectedMethod)) {
                        continue;
                    }

                    // Check the generic return type to handle ResponseEntity<T>, List<T>, etc.
                    Type returnType = reflectedMethod.getGenericReturnType();
                    Optional<Class<?>> entityType = findFirstEntityType(returnType);

                    if (entityType.isEmpty()) {
                        // Also check annotated return type as a fallback
                        entityType = findFirstEntityType(reflectedMethod.getAnnotatedReturnType().getType());
                    }

                    if (entityType.isPresent()) {
                        String message = String.format("Entity returned from REST controller: %s#%s - return type '%s' contains entity '%s'", controllerClass.getFullName(),
                                archMethod.getName(), returnType.getTypeName(), entityType.get().getName());
                        violations.add(message);
                        // Note: We don't add to events because we want to allow a configurable number of violations
                        // The assertion at the end will check if the count is within the allowed limit
                    }
                }
            }
        };

        classes().that().resideInAPackage(getModuleWithSubpackage()).and().areAnnotatedWith(RestController.class).should(condition).allowEmptyShould(true).check(productionClasses);

        int maxAllowed = getMaxEntityReturnViolations();
        assertThat(violations).as(
                "Entity return type violations in module %s (expected exactly: %d, found: %d). "
                        + "Self-enforcing ratchet: if you reduce violations, lower this override; if you add a new violation, fix it instead of inflating the override. "
                        + "TODO: This number should be reduced to 0 by using DTOs instead of entities. " + "See the database documentation for guidelines on DTO usage.",
                getModulePackage(), maxAllowed, violations.size()).hasSize(maxAllowed);
    }

    /**
     * Verifies that REST controllers in this module do not accept @Entity types in @RequestBody or @RequestPart parameters.
     * <p>
     * This test checks:
     * <ul>
     * <li>Direct entity parameters (e.g., {@code @RequestBody User user})</li>
     * <li>Collections containing entities (e.g., {@code @RequestBody List<User> users})</li>
     * <li>Nested generic types containing entities</li>
     * </ul>
     * <p>
     * The test allows up to {@link #getMaxEntityInputViolations()} violations to support
     * incremental migration to DTOs. This number should be reduced to 0 over time.
     */
    @Test
    protected void restControllersMustNotAcceptEntitiesInRequestBodyOrPart() {
        List<String> violations = new ArrayList<>();

        var condition = new ArchCondition<JavaClass>("not use @Entity types in @RequestBody/@RequestPart parameters") {

            @Override
            public void check(JavaClass controllerClass, ConditionEvents events) {
                Class<?> reflectedController = controllerClass.reflect();

                for (JavaMethod archMethod : controllerClass.getMethods()) {
                    // Keep output clean (ignore inherited methods)
                    if (!archMethod.getOwner().equals(controllerClass)) {
                        continue;
                    }

                    Method reflectedMethod = findMatchingDeclaredMethod(reflectedController, archMethod);
                    if (reflectedMethod == null) {
                        // synthetic/bridge methods etc. - skip safely
                        continue;
                    }

                    // Only actual REST endpoints matter — skip private/helper methods.
                    if (!isRestEndpointMethod(reflectedMethod)) {
                        continue;
                    }

                    Parameter[] parameters = reflectedMethod.getParameters();
                    AnnotatedType[] annotatedParameterTypes = reflectedMethod.getAnnotatedParameterTypes();

                    for (int i = 0; i < parameters.length; i++) {
                        Parameter parameter = parameters[i];

                        boolean isRequestBody = parameter.isAnnotationPresent(RequestBody.class);
                        boolean isRequestPart = parameter.isAnnotationPresent(RequestPart.class);

                        if (!isRequestBody && !isRequestPart) {
                            continue;
                        }

                        Type parameterType = parameter.getParameterizedType();
                        Optional<Class<?>> entityType = findFirstEntityType(parameterType);

                        if (entityType.isEmpty()) {
                            // defensive: also check annotated view
                            entityType = findFirstEntityType(annotatedParameterTypes[i].getType());
                        }

                        if (entityType.isPresent()) {
                            String annotation = isRequestBody ? "@RequestBody" : "@RequestPart";
                            String message = String.format("Incoming entity used in %s: %s#%s - parameter '%s' has (or contains) entity type '%s' (parameter type: %s)", annotation,
                                    controllerClass.getFullName(), archMethod.getName(), parameter.getName(), entityType.get().getName(), parameterType.getTypeName());
                            violations.add(message);
                            // Note: We don't add to events because we want to allow a configurable number of violations
                            // The assertion at the end will check if the count is within the allowed limit
                        }
                    }
                }
            }
        };

        classes().that().resideInAPackage(getModuleWithSubpackage()).and().areAnnotatedWith(RestController.class).should(condition).allowEmptyShould(true).check(productionClasses);

        int maxAllowed = getMaxEntityInputViolations();
        assertThat(violations).as(
                "Entity input violations in module %s (expected exactly: %d, found: %d). "
                        + "Self-enforcing ratchet: if you reduce violations, lower this override; if you add a new violation, fix it instead of inflating the override. "
                        + "TODO: This number should be reduced to 0 by using DTOs instead of entities. " + "See the database documentation for guidelines on DTO usage.",
                getModulePackage(), maxAllowed, violations.size()).hasSize(maxAllowed);
    }

    /**
     * Verifies that DTO classes in this module do not contain fields that reference @Entity types.
     * <p>
     * This test prevents the "lazy wrapper" anti-pattern where developers create DTOs that simply
     * wrap entity objects instead of extracting only the necessary fields. Such DTOs defeat the
     * entire purpose of using DTOs for:
     * <ul>
     * <li>Security (entity fields are still exposed)</li>
     * <li>Performance (entire entity graph is still serialized)</li>
     * <li>API stability (entity changes still affect the API)</li>
     * </ul>
     * <p>
     * DTOs should only contain:
     * <ul>
     * <li>Primitive types and their wrappers (String, Long, Integer, Boolean, etc.)</li>
     * <li>Date/time types (ZonedDateTime, Instant, LocalDate, etc.)</li>
     * <li>Enums</li>
     * <li>Other DTOs (for nested data structures)</li>
     * <li>Collections of the above types</li>
     * </ul>
     */
    @Test
    protected void dtosMustNotContainEntityFields() {
        List<String> violations = new ArrayList<>();

        var condition = new ArchCondition<JavaClass>("not contain fields that reference @Entity types") {

            @Override
            public void check(JavaClass dtoClass, ConditionEvents events) {
                Class<?> reflectedDto = dtoClass.reflect();

                // Check all declared fields (including record components which appear as fields)
                for (Field field : reflectedDto.getDeclaredFields()) {
                    // Skip synthetic fields (like $VALUES in enums, or compiler-generated fields)
                    if (field.isSynthetic()) {
                        continue;
                    }

                    Type fieldType = field.getGenericType();
                    Optional<Class<?>> entityType = findFirstEntityType(fieldType);

                    if (entityType.isPresent()) {
                        String message = String.format(
                                "DTO contains entity field: %s.%s - field type '%s' contains entity '%s'. "
                                        + "DTOs must not reference entities; extract only the needed primitive/DTO fields instead.",
                                dtoClass.getFullName(), field.getName(), fieldType.getTypeName(), entityType.get().getName());
                        violations.add(message);
                        // Note: We don't add to events because we want to allow a configurable number of violations
                        // The assertion at the end will check if the count is within the allowed limit
                    }
                }
            }
        };

        // Check classes ending with "DTO" (case-sensitive) - this is the naming convention for DTOs
        classes().that().resideInAPackage(getModuleWithSubpackage()).and().haveSimpleNameEndingWith("DTO").should(condition).allowEmptyShould(true).check(productionClasses);

        int maxAllowed = getMaxDtoEntityFieldViolations();
        assertThat(violations).as(
                "DTO entity field violations in module %s (expected exactly: %d, found: %d). "
                        + "Self-enforcing ratchet: if you reduce violations, lower this override; if you add a new violation, fix it instead of inflating the override. "
                        + "TODO: This number should be reduced to 0 by removing entity references from DTOs. "
                        + "DTOs should only contain primitive types, date/time types, enums, and other DTOs. " + "See the database documentation for guidelines on DTO usage.",
                getModulePackage(), maxAllowed, violations.size()).hasSize(maxAllowed);
    }

    /**
     * Whether the given controller method is an actual HTTP endpoint, i.e. it is (meta-)annotated with
     * {@link RequestMapping}. This covers {@code @GetMapping}/{@code @PostMapping}/{@code @PutMapping}/
     * {@code @DeleteMapping}/{@code @PatchMapping} (all meta-annotated with {@code @RequestMapping}) and any
     * custom mapping annotation. Private/helper methods (e.g. DTO&lt;-&gt;entity mappers) that merely live in a
     * {@code @RestController} but carry no mapping annotation are not endpoints and must not be flagged.
     */
    protected static boolean isRestEndpointMethod(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    /**
     * Matches ArchUnit's JavaMethod to the declared reflective Method on the controller class.
     * Uses name + raw parameter types.
     */
    protected static Method findMatchingDeclaredMethod(Class<?> owner, JavaMethod archMethod) {
        String name = archMethod.getName();
        Class<?>[] rawParamTypes = archMethod.getRawParameterTypes().stream().map(JavaClass::reflect).toArray(Class<?>[]::new);

        try {
            return owner.getDeclaredMethod(name, rawParamTypes);
        }
        catch (NoSuchMethodException ex) {
            // Fallback: best-effort match by name + same count + exact raw types
            for (Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (method.getParameterCount() != rawParamTypes.length) {
                    continue;
                }
                Class<?>[] actual = method.getParameterTypes();
                boolean same = true;
                for (int i = 0; i < actual.length; i++) {
                    if (!actual[i].equals(rawParamTypes[i])) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    return method;
                }
            }
            return null;
        }
    }

    /**
     * Returns the first encountered @Entity class inside a Java reflection Type.
     * Handles nested generics, arrays, generic arrays, and wildcards.
     */
    protected static Optional<Class<?>> findFirstEntityType(Type type) {
        switch (type) {
            case null -> {
                return Optional.empty();
            }
            case Class<?> cls -> {
                if (isEntity(cls)) {
                    return Optional.of(cls);
                }
                if (cls.isArray()) {
                    return findFirstEntityType(cls.getComponentType());
                }
                return Optional.empty();
            }
            case ParameterizedType parameterizedType -> {
                // A Class<X> (e.g. Class<? extends LectureUnit>) is a type token, not an embedded entity value:
                // its type argument names a type, it does not serialize an entity instance. Don't recurse into it.
                if (parameterizedType.getRawType() == Class.class) {
                    return Optional.empty();
                }
                for (Type arg : parameterizedType.getActualTypeArguments()) {
                    Optional<Class<?>> found = findFirstEntityType(arg);
                    if (found.isPresent()) {
                        return found;
                    }
                }
                // raw type itself usually isn't entity (List, Optional...), but check anyway
                return findFirstEntityType(parameterizedType.getRawType());
            }
            case GenericArrayType genericArrayType -> {
                return findFirstEntityType(genericArrayType.getGenericComponentType());
            }
            case WildcardType wildcardType -> {
                for (Type upper : wildcardType.getUpperBounds()) {
                    Optional<Class<?>> found = findFirstEntityType(upper);
                    if (found.isPresent()) {
                        return found;
                    }
                }
                for (Type lower : wildcardType.getLowerBounds()) {
                    Optional<Class<?>> found = findFirstEntityType(lower);
                    if (found.isPresent()) {
                        return found;
                    }
                }
                return Optional.empty();
            }
            default -> {
            }
        }

        return Optional.empty();
    }

    protected static boolean isEntity(Class<?> cls) {
        return cls.isAnnotationPresent(Entity.class);
    }
}
