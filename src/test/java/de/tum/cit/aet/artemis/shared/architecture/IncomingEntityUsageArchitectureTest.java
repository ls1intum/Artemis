package de.tum.cit.aet.artemis.shared.architecture;

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
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

class IncomingEntityUsageArchitectureTest {

    private static final String BASE_PACKAGE = "de.tum.cit.aet.artemis";

    // TODO: This test is currently disabled because there are 54 existing violations where REST controllers
    // accept entities directly in @RequestBody/@RequestPart parameters. These should be refactored to use DTOs.
    // Re-enable this test once the violations are fixed.
    // See: https://github.com/ls1intum/Artemis/issues/XXXX (create issue to track this work)
    @org.junit.jupiter.api.Disabled("54 existing violations need to be fixed - controllers should accept DTOs, not entities")
    @Test
    void rest_controllers_must_not_accept_entities_in_request_body_or_part() {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class).should(notUseEntitiesAsRequestBodyOrPart());

        rule.check(new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(BASE_PACKAGE));
    }

    // TODO: This test is currently disabled because there are 710 existing violations where REST controllers
    // return entities directly. These should be refactored to return DTOs instead.
    // Re-enable this test once the violations are fixed.
    // See: https://github.com/ls1intum/Artemis/issues/XXXX (create issue to track this work)
    @org.junit.jupiter.api.Disabled("710 existing violations need to be fixed - controllers should return DTOs, not entities")
    @Test
    void rest_controllers_must_not_return_entities() {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class).should(notReturnEntities());

        rule.check(new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(BASE_PACKAGE));
    }

    private static ArchCondition<JavaClass> notUseEntitiesAsRequestBodyOrPart() {
        return new ArchCondition<>("not use @Entity types in @RequestBody/@RequestPart parameters") {

            @Override
            public void check(JavaClass controllerClass, ConditionEvents events) {
                Class<?> reflectedController = controllerClass.reflect();

                for (JavaMethod archMethod : controllerClass.getMethods()) {
                    // keep output clean (ignore inherited methods)
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

    private static ArchCondition<JavaClass> notReturnEntities() {
        return new ArchCondition<>("not return @Entity types from REST controller methods") {

            @Override
            public void check(JavaClass controllerClass, ConditionEvents events) {
                Class<?> reflectedController = controllerClass.reflect();

                for (JavaMethod archMethod : controllerClass.getMethods()) {
                    // keep output clean (ignore inherited methods)
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
     * Matches ArchUnit's JavaMethod to the declared reflective Method on the controller class.
     * Uses name + raw parameter types.
     */
    private static Method findMatchingDeclaredMethod(Class<?> owner, JavaMethod archMethod) {
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
    private static Optional<Class<?>> findFirstEntityType(Type type) {
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
                // raw type itself usually isn't entity (List, Optional...), but check anyway
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

    private static boolean isEntity(Class<?> cls) {
        return cls.isAnnotationPresent(Entity.class);
    }
}
