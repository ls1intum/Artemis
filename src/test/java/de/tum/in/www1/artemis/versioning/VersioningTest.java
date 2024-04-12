package de.tum.in.www1.artemis.versioning;

import static org.assertj.core.api.Assertions.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class VersioningTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private List<Integer> apiVersions;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Tests for each controller if there exist two methods representing the same API route while the annotated
     * version ranges collide
     */
    // TODO: Find solution to test this for all different profiles
    @Test
    void testDuplicateRoutes() {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        // Only search Artemis endpoints
        var filteredMap = map.entrySet().stream().filter(entry -> entry.getValue().getBeanType().getPackage().getName().startsWith("de.tum.in.www1.artemis"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, List<Method>> methodsGroupedByPathIgnoreVersion = filteredMap.entrySet().stream().collect(Collectors.groupingBy(entry -> {
            var name = entry.getKey().getName();
            if (name == null) {
                return "";
            }
            return name.replaceFirst("v[1-9][0-9]*/", "");
        }, Collectors.mapping(entry -> entry.getValue().getMethod(), Collectors.toCollection(ArrayList::new))));

        // Check collisions for each endpoint
        for (var endpoint : methodsGroupedByPathIgnoreVersion.entrySet()) {
            checkCollisionForEndpoint(endpoint);
        }
    }

    private void checkCollisionForEndpoint(Map.Entry<String, List<Method>> endpoint) {
        var methods = endpoint.getValue();
        checkVersionSyntax(methods);

        var ignoredMethods = methods.stream().filter(method -> {
            var annotation = method.getAnnotation(IgnoreGlobalMapping.class);
            return annotation != null && annotation.ignoreCollision();
        }).toList();
        methods.removeAll(ignoredMethods);

        var httpMethodsByMappingAnnotations = methods.stream().collect(Collectors.toMap(Function.identity(), this::getUniqueMappingAnnotation));
        var httpMethodsWithMultipleMappingAnnotations = httpMethodsByMappingAnnotations.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var httpMethodsWithNoMappingAnnotations = httpMethodsByMappingAnnotations.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList();
        var httpMethodSplit = httpMethodsByMappingAnnotations.entrySet().stream()
                .filter(entry -> !httpMethodsWithMultipleMappingAnnotations.containsKey(entry.getKey()) && !httpMethodsWithNoMappingAnnotations.contains(entry.getKey()))
                .collect(Collectors.groupingBy(entry -> entry.getValue().getFirst(), Collectors.mapping(Map.Entry::getKey, Collectors.toCollection(ArrayList::new))));

        // Make sure that the exceptions were manually specified
        httpMethodsWithMultipleMappingAnnotations.forEach((method, list) -> checkGlobalMappingForMethod(method));
        httpMethodsWithNoMappingAnnotations.forEach(this::checkGlobalMappingForMethod);

        // Check collisions for each HTTP method
        for (var httpMethod : httpMethodSplit.entrySet()) {
            var methodList = httpMethod.getValue();
            // If there is no mapping it counts for all methods
            methodList.addAll(httpMethodsWithNoMappingAnnotations);
            // If there are multiple mappings, we want to calculate only the relevant collisions
            methodList.addAll(
                    httpMethodsWithMultipleMappingAnnotations.entrySet().stream().filter(entry -> entry.getValue().contains(httpMethod.getKey())).map(Map.Entry::getKey).toList());

            // If there is no more than one method, there can be no collision
            if (methodList.size() < 2) {
                new VersionRangesRequestCondition(apiVersions, (VersionRange) getClass().getMethods()[0].getAnnotation(VersionRanges.class));
                break;
            }

            // Check for actual collision within endpoint and HTTP method
            checkCollisionForEndpoints(methodList);
        }
    }

    private void checkCollisionForEndpoints(List<Method> methodList) {
        while (!methodList.isEmpty()) {
            var method = methodList.removeFirst();

            VersionRangesRequestCondition condition = new VersionRangesRequestCondition(apiVersions, getVersionRangeFromMethod(method));

            for (var collision : methodList) {
                if (condition.collide(new VersionRangesRequestCondition(apiVersions, getVersionRangeFromMethod(collision)))) {
                    fail("Version ranges of class " + collision.getDeclaringClass().getName() + " of method " + getPrintableMethodName(method)
                            + " collide with version ranges of method " + collision.getName() + "() of same " + "class.");
                }
            }
        }
    }

    private String getPrintableMethodName(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
    }

    private void checkGlobalMappingForMethod(Method method) {
        var ignoreGlobalMappingAnnotation = method.getAnnotation(IgnoreGlobalMapping.class);
        if (ignoreGlobalMappingAnnotation == null || !ignoreGlobalMappingAnnotation.ignoreUniqueMethods()) {
            fail("Invalid mapping annotation for " + getPrintableMethodName(method)
                    + ". Expected exactly one of: GetMapping, PatchMapping, PostMapping, PutMapping, DeleteMapping");
        }
    }

    /**
     * Returns all {@link VersionRange} annotations of a method. If the method is annotated with {@link VersionRanges}, the contained annotations are returned.
     *
     * @param method The method to get the annotations from
     * @return An array of {@link VersionRange} annotations
     */
    private VersionRange[] getVersionRangeFromMethod(Method method) {
        var versionRanges = method.getAnnotation(VersionRanges.class);
        var versionRange = method.getAnnotation(VersionRange.class);
        if (versionRanges != null && versionRange != null) {
            fail("Method " + getPrintableMethodName(method) + " has both VersionRanges and VersionRange annotation.");
        }
        if (versionRanges != null) {
            return versionRanges.value();
        }
        if (versionRange != null) {
            return new VersionRange[] { versionRange };
        }
        return new VersionRange[0];
    }

    /**
     * Check that not both, {@link VersionRanges} and {@link VersionRange}, are present
     *
     * @param methods List of methods to check
     */
    private void checkVersionSyntax(List<Method> methods) {
        for (var method : methods) {
            var versionRanges = method.getAnnotation(VersionRanges.class);
            var versionRange = method.getAnnotation(VersionRange.class);
            if (versionRanges != null && versionRange != null) {
                fail("Method " + getPrintableMethodName(method) + " has both VersionRanges and VersionRange annotation.");
            }
        }
    }

    /**
     * Returns all mapping annotations of a method.
     *
     * @param method The method to get the annotations from
     * @return The mapping annotations of the method.
     */
    private List<Annotation> getUniqueMappingAnnotation(Method method) {
        List<Annotation> result = new ArrayList<>();
        if (method.getAnnotation(GetMapping.class) != null) {
            result.add(method.getAnnotation(GetMapping.class));
        }
        if (method.getAnnotation(PatchMapping.class) != null) {
            result.add(method.getAnnotation(PatchMapping.class));
        }
        if (method.getAnnotation(PostMapping.class) != null) {
            result.add(method.getAnnotation(PostMapping.class));
        }
        if (method.getAnnotation(PutMapping.class) != null) {
            result.add(method.getAnnotation(PutMapping.class));
        }
        if (method.getAnnotation(DeleteMapping.class) != null) {
            result.add(method.getAnnotation(DeleteMapping.class));
        }
        return result;
    }
}
