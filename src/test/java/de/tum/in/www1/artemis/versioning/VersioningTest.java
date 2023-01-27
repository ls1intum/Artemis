package de.tum.in.www1.artemis.versioning;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class VersioningTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Tests for each controller if there exist two methods representing the same API route while the annotated
     * version ranges collide
     */
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
        }, Collectors.mapping(entry -> entry.getValue().getMethod(), Collectors.toList())));

        // Check collisions for each endpoint
        for (var endpoint : methodsGroupedByPathIgnoreVersion.entrySet()) {
            checkCollisionForEndpoint(endpoint);
        }
    }

    private void checkCollisionForEndpoint(Map.Entry<String, List<Method>> endpoint) {
        var methods = endpoint.getValue();
        var httpMethodSplit = methods.stream().collect(Collectors.groupingBy(this::getMappingAnnotation));
        // Check collisions for each HTTP method
        for (var httpMethod : httpMethodSplit.entrySet()) {
            var key = httpMethod.getKey();
            if (key.isEmpty()) {
                fail("Invalid mapping annotation for " + endpoint.getKey() + ". Expected one of: GetMapping, PostMapping, PutMapping, DeleteMapping");
            }
            var methodList = httpMethod.getValue();
            checkVersionSyntax(methodList);

            // If there is no more than one method, there can be no collision
            if (methodList.size() < 2) {
                new VersionRangesRequestCondition((VersionRange) getClass().getMethods()[0].getAnnotation(VersionRanges.class));
                break;
            }

            // Check for actual collision within endpoint and HTTP method
            checkCollisionForEndpoints(methodList);
        }
    }

    private void checkCollisionForEndpoints(List<Method> methodList) {
        while (!methodList.isEmpty()) {
            var method = methodList.remove(0);

            VersionRangesRequestCondition condition = new VersionRangesRequestCondition(getVersionRangeFromMethod(method));

            for (var collision : methodList) {
                if (condition.collide(new VersionRangesRequestCondition(getVersionRangeFromMethod(collision)))) {
                    fail("Version ranges of class " + collision.getDeclaringClass().getName() + " of method " + method.getName() + "() collide with version ranges of method "
                            + collision.getName() + "() of same " + "class.");
                }
            }
        }
    }

    /**
     * Returns all {@link VersionRange} annotations of a method. If the method is annotated with {@link VersionRanges}, the contained annotations are returned.
     * @param method The method to get the annotations from
     * @return An array of {@link VersionRange} annotations
     */
    private VersionRange[] getVersionRangeFromMethod(Method method) {
        var versionRanges = method.getAnnotation(VersionRanges.class);
        var versionRange = method.getAnnotation(VersionRange.class);
        if (versionRanges != null && versionRange != null) {
            fail("Method " + method.getName() + " has both VersionRanges and VersionRange annotation.");
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
     * @param methods List of methods to check
     */
    private void checkVersionSyntax(List<Method> methods) {
        for (var method : methods) {
            var versionRanges = method.getAnnotation(VersionRanges.class);
            var versionRange = method.getAnnotation(VersionRange.class);
            if (versionRanges != null && versionRange != null) {
                fail("Method " + method.getName() + " has both VersionRanges and VersionRange annotation.");
            }
        }
    }

    /**
     * Returns the mapping annotation of a method if exists as an {@link Optional}.
     * @param method The method to get the annotation from
     * @return The mapping annotation of the method wrapped in an {@link Optional}.
     */
    private Optional<Annotation> getMappingAnnotation(Method method) {
        if (method.getAnnotation(GetMapping.class) != null) {
            return Optional.of(method.getAnnotation(GetMapping.class));
        }
        if (method.getAnnotation(PostMapping.class) != null) {
            return Optional.of(method.getAnnotation(PostMapping.class));
        }
        if (method.getAnnotation(PutMapping.class) != null) {
            return Optional.of(method.getAnnotation(PutMapping.class));
        }
        if (method.getAnnotation(DeleteMapping.class) != null) {
            return Optional.of(method.getAnnotation(DeleteMapping.class));
        }
        return Optional.empty();
    }
}
