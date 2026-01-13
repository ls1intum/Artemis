package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Optional;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Abstract base class for module-specific entity usage architecture tests.
 * <p>
 * These tests verify that REST controllers in a module:
 * <ul>
 * <li>Do not return @Entity types directly (should return DTOs instead)</li>
 * <li>Do not accept @Entity types in @RequestBody/@RequestPart parameters (should accept DTOs instead)</li>
 * </ul>
 * <p>
 * To use this class:
 * <ol>
 * <li>Create a subclass in your module's architecture package</li>
 * <li>Implement {@link #getModulePackage()} to return the module's base package</li>
 * <li>Optionally override test methods with @Disabled annotation if the module is not yet migrated</li>
 * </ol>
 */
public abstract class AbstractModuleEntityUsageArchitectureTest extends AbstractArchitectureTest implements ModuleArchitectureTest {

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
     * Override this method with @Disabled annotation if the module is not yet migrated.
     */
    @Test
    protected void restControllersMustNotReturnEntities() {
        ArchRule rule = classes().that().resideInAPackage(getModuleWithSubpackage()).and().areAnnotatedWith(RestController.class).should(notReturnEntities());

        rule.allowEmptyShould(true).check(productionClasses);
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
     * Override this method with @Disabled annotation if the module is not yet migrated.
     */
    @Test
    protected void restControllersMustNotAcceptEntitiesInRequestBodyOrPart() {
        ArchRule rule = classes().that().resideInAPackage(getModuleWithSubpackage()).and().areAnnotatedWith(RestController.class).should(notUseEntitiesAsRequestBodyOrPart());

        rule.allowEmptyShould(true).check(productionClasses);
    }

    /**
     * Creates an ArchUnit condition that checks if REST controller methods return entities.
     *
     * @return the ArchCondition for checking entity returns
     */
    protected ArchCondition<JavaClass> notReturnEntities() {
        return new ArchCondition<>("not return @Entity types from REST controller methods") {

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

                        events.add(SimpleConditionEvent.violated(controllerClass, message));
                    }
                }
            }
        };
    }

    /**
     * Creates an ArchUnit condition that checks if REST controller methods accept entities in @RequestBody/@RequestPart parameters.
     *
     * @return the ArchCondition for checking entity inputs
     */
    protected ArchCondition<JavaClass> notUseEntitiesAsRequestBodyOrPart() {
        return new ArchCondition<>("not use @Entity types in @RequestBody/@RequestPart parameters") {

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

                            events.add(SimpleConditionEvent.violated(controllerClass, message));
                        }
                    }
                }
            }
        };
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
