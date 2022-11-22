package de.tum.in.www1.artemis.authorization;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.security.annotations.ManualConfig;

/**
 * Contains the one automatic test covering all rest endpoints for authorization tests.
 */
class AuthorizationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizationTestService authorizationTestService;

    @Test
    void testEndpoints() throws InvocationTargetException, IllegalAccessException {
        var requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        // Only search our Artemis endpoints
        map = map.entrySet().stream().filter(entry -> entry.getValue().getBeanType().getPackage().getName().startsWith("de.tum.in.www1.artemis")
                && entry.getValue().getMethod().getAnnotation(ManualConfig.class) == null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<Class<?>, Set<String>> classReports = new HashMap<>();
        Map<Method, Set<String>> methodReports = new HashMap<>();

        // Test each endpoint and collect the reports
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : map.entrySet()) {
            testEndpoint(entry.getKey(), entry.getValue(), classReports, methodReports);
        }

        // take only distinct reports and add them to the class reports
        methodReports.forEach((methodKey, value) -> classReports.computeIfAbsent(methodKey.getDeclaringClass(), key -> new HashSet<>()).addAll(value));

        // Print the reports and fail the tests if there are any. Otherwise, the test succeeds
        if (!classReports.isEmpty()) {
            fail(authorizationTestService.formatReportString(classReports));
        }
    }

    /**
     * Tests a single endpoint and collects the reports
     * Additional tests should be added here.
     *
     * @param info          The request mapping info of the endpoint
     * @param method        The handler method of the endpoint
     * @param classReports  The current class reports
     * @param methodReports The current method reports
     */
    private void testEndpoint(RequestMappingInfo info, HandlerMethod method, Map<Class<?>, Set<String>> classReports, Map<Method, Set<String>> methodReports)
            throws InvocationTargetException, IllegalAccessException {
        checkForAnnotation(method, classReports, methodReports);
    }

    /**
     * Checks if the endpoint (including the class itself) has a valid endpoint. Has to be adapted during migration.
     *
     * @param method        The handler method of the endpoint
     * @param classReports  The current class reports
     * @param methodReports The current method reports
     */
    private void checkForAnnotation(HandlerMethod method, Map<Class<?>, Set<String>> classReports, Map<Method, Set<String>> methodReports)
            throws InvocationTargetException, IllegalAccessException {
        Method javaMethod = method.getMethod();
        Class<?> javaClass = javaMethod.getDeclaringClass();
        List<Annotation> methodAnnotations = authorizationTestService.getAuthAnnotations(javaMethod);
        List<Annotation> classAnnotations = authorizationTestService.getAuthAnnotations(javaClass);

        // Cover edge cases: No annotation, Multiple method annotations, Multiple class annotations
        if (methodAnnotations.isEmpty() && classAnnotations.isEmpty()) {
            authorizationTestService.addElement(methodReports, javaMethod, "No authorization annotation found for " + javaMethod.getName() + "().");
        }
        if (methodAnnotations.size() > 1) {
            authorizationTestService.addElement(methodReports, javaMethod, "Multiple method authorization annotations found for " + javaMethod.getName() + "().");
        }
        if (classAnnotations.size() > 1) {
            authorizationTestService.addElement(classReports, javaClass, "Multiple class authorization annotations found.");
        }

        // Cover default cases: One class annotation, one method annotation, mixture of class and method annotations
        // The mixture case gets executed in addition to the edge cases to give the developer all information at once
        if (classAnnotations.size() == 1 && methodAnnotations.isEmpty()) {
            authorizationTestService.checkClassAnnotation(classAnnotations.get(0), javaClass, javaMethod, classReports);
        }
        else if (methodAnnotations.size() == 1 && classAnnotations.isEmpty()) {
            Annotation annotation = methodAnnotations.get(0);
            if (annotation.annotationType().equals(PreAuthorize.class)) {
                authorizationTestService.checkMethodAnnotation(annotation, javaMethod, methodReports);
            }
        }
        else if (!methodAnnotations.isEmpty() && !classAnnotations.isEmpty()) {
            // Collision between class and method annotations
            authorizationTestService.addElement(methodReports, javaMethod,
                    "Collision between class and method authorization annotations found for " + javaMethod.getName() + "(). Collisions should be " + "avoided" + ".");
        }
    }
}
