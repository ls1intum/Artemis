package de.tum.in.www1.artemis.authorization;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.assertj.core.api.Fail.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import de.tum.in.www1.artemis.security.annotations.*;

/**
 * This service is used to check if the authorization annotations are used correctly.
 */
@Service
public class AuthorizationTestService {

    private static final Set<Class<? extends Annotation>> AUTHORIZATION_ANNOTATIONS = Set.of(EnforceAdmin.class, EnforceAtLeastInstructor.class, EnforceAtLeastEditor.class,
            EnforceAtLeastTutor.class, EnforceAtLeastStudent.class, EnforceNothing.class, PreAuthorize.class);

    private static final String REST_BASE_PATH = "/api";

    private static final String REST_ADMIN_PATH = REST_BASE_PATH + "/admin";

    private static final String REST_PUBLIC_PATH = REST_BASE_PATH + "/public";

    /**
     * Tests all endpoints and prints the reports
     *
     * @param endpointMap The map of all endpoints
     */
    public void testAllEndpoints(Map<RequestMappingInfo, HandlerMethod> endpointMap) {
        var endpointsToBeTested = endpointMap.entrySet().stream().filter(entry -> validEndpointToTest(entry.getValue(), false))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        testEndpoints(endpointsToBeTested);
    }

    /**
     * Tests only endpoints that depend on a specific non-core profile and that most likely is only relevant for the currently running test environment.
     *
     * @param endpointMap The map of all endpoints
     */
    public void testConditionalEndpoints(Map<RequestMappingInfo, HandlerMethod> endpointMap) {
        var endpointsToBeTested = endpointMap.entrySet().stream().filter(entry -> validEndpointToTest(entry.getValue(), true))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        testEndpoints(endpointsToBeTested);
    }

    /**
     * Tests the given endpoints and prints the reports
     *
     * @param endpointMap The map of all endpoints to test
     */
    private void testEndpoints(Map<RequestMappingInfo, HandlerMethod> endpointMap) {
        Map<Class<?>, Set<String>> classReports = new HashMap<>();
        Map<Method, Set<String>> methodReports = new HashMap<>();

        // Test each endpoint and collect the reports
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : endpointMap.entrySet()) {
            testEndpoint(entry.getKey(), entry.getValue(), methodReports);
        }

        printReports(classReports, methodReports);
    }

    /**
     * Evaluates whether a given endpoint should be tested. Only endpoints defined in artemis and not annotated with @ManualConfig are tested. If onlyConditional is true, only
     * endpoints
     * that depend on a specific profile are tested.
     *
     * @param method          The handler method of the endpoint
     * @param onlyConditional Whether only endpoints that depend on a profile should be tested
     * @return true if the endpoint should be tested, false otherwise
     */
    public boolean validEndpointToTest(HandlerMethod method, boolean onlyConditional) {
        return method.getBeanType().getPackage().getName().startsWith("de.tum.in.www1.artemis") && method.getMethod().getAnnotation(ManualConfig.class) == null
                && (!onlyConditional || isConditionalEndpoint(method));
    }

    /**
     * Evaluates whether a given endpoint depends on a specific profile
     *
     * @param handlerMethod The handler method of the endpoint
     * @return true if the endpoint depends on a profile, false otherwise
     */
    private boolean isConditionalEndpoint(HandlerMethod handlerMethod) {
        var methodProfileAnnotation = handlerMethod.getMethod().getAnnotation(Profile.class);
        var classProfileAnnotation = handlerMethod.getMethod().getDeclaringClass().getAnnotation(Profile.class);
        // No null-check required for classes because we have tests ensuring Profile annotations on classes
        return (methodProfileAnnotation != null && isNonCoreProfile(methodProfileAnnotation)) || isNonCoreProfile(classProfileAnnotation);
    }

    private boolean isNonCoreProfile(Profile profileAnnotation) {
        return !(profileAnnotation.value().length == 1 && profileAnnotation.value()[0].equals(PROFILE_CORE));
    }

    /**
     * Tests a single endpoint and collects the reports
     * Additional tests should be added here.
     *
     * @param info          The request mapping info of the endpoint
     * @param method        The handler method of the endpoint
     * @param methodReports The current method reports
     */
    private void testEndpoint(RequestMappingInfo info, HandlerMethod method, Map<Method, Set<String>> methodReports) {
        checkForPath(info, method, methodReports);
    }

    /**
     * Checks if the path of the endpoint has the correct prefix. If the method has no single authorization annotation, the method returns.
     *
     * @param info          The request mapping info of the endpoint
     * @param method        The handler method of the endpoint
     * @param methodReports The current method reports
     */
    private void checkForPath(RequestMappingInfo info, HandlerMethod method, Map<Method, Set<String>> methodReports) {
        Method javaMethod = method.getMethod();
        Set<String> patterns = Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues();
        Annotation annotation = getSingleAuthAnnotation(javaMethod);
        if (annotation == null) {
            // We already logged an error in this case
            return;
        }
        String annotationType = annotation.annotationType().getSimpleName();

        switch (annotationType) {
            case "EnforceAdmin" -> {
                for (String pattern : patterns) {
                    if (!pattern.startsWith(REST_ADMIN_PATH)) {
                        addElement(methodReports, javaMethod,
                                "Expect path of method " + javaMethod.getName() + " annotated with @EnforceAdmin to start with " + REST_ADMIN_PATH + " but is " + pattern + ".");
                    }
                }
            }
            case "EnforceAtLeastInstructor", "EnforceAtLeastEditor", "EnforceAtLeastTutor", "EnforceAtLeastStudent" -> {
                for (String pattern : patterns) {
                    if (!pattern.startsWith(REST_BASE_PATH)) {
                        addElement(methodReports, javaMethod, "Expect path of method " + javaMethod.getName() + " annotated with @" + annotationType + " to start with "
                                + REST_BASE_PATH + " but is " + pattern + ".");
                    }
                }
            }
            case "EnforceNothing" -> {
                for (String pattern : patterns) {
                    if (!pattern.startsWith(REST_PUBLIC_PATH)) {
                        addElement(methodReports, javaMethod,
                                "Expect path of method " + javaMethod.getName() + " annotated with @EnforceNothing to start with " + REST_PUBLIC_PATH + " but is " + pattern + ".");
                    }
                }
            }
            default -> addElement(methodReports, javaMethod, "Unsupported annotation type " + annotationType + " for method " + javaMethod.getName() + "().");
        }
    }

    /**
     * Returns the authorization annotation of the given method if it exists, null otherwise or if multiple annotations exist.
     *
     * @param method The method to check
     * @return The authorization annotation or null
     */
    private Annotation getSingleAuthAnnotation(Method method) {
        List<Annotation> annotations = getAuthAnnotations(method);
        if (annotations.size() == 1) {
            return annotations.get(0);
        }
        return null;
    }

    /**
     * Returns all annotations on the given method that are relevant to authorization
     *
     * @param method the method to check
     * @return List of relevant annotations
     */
    private List<Annotation> getAuthAnnotations(Method method) {
        var annotations = Arrays.asList(method.getAnnotations());
        return annotations.stream().filter(annotation -> AUTHORIZATION_ANNOTATIONS.contains(annotation.annotationType())).toList();
    }

    /**
     * Wrapper method to add a new report to the report map
     * Adds the report to the matching existing list, otherwise creates a new modifiable list
     *
     * @param <T>          The type of the report
     * @param reportsMap   The current collection of reports
     * @param reportObject The report object the report is about
     * @param report       The report to add
     */
    private <T> void addElement(Map<T, Set<String>> reportsMap, T reportObject, String report) {
        if (reportsMap.containsKey(reportObject)) {
            reportsMap.get(reportObject).add(report);
        }
        else {
            reportsMap.put(reportObject, new HashSet<>(Set.of(report)));
        }
    }

    /**
     * Creates a formatted string representation of the given reports
     *
     * @param reportsMap The reports to format
     * @return The formatted string
     */
    private String formatReportString(Map<Class<?>, Set<String>> reportsMap) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Some endpoints contain illegal authorization configurations:").append(System.lineSeparator());
        reportsMap.forEach((clazz, reportList) -> {
            stringBuilder.append("Class ").append(clazz.getSimpleName()).append(" has the following illegal configurations:").append(System.lineSeparator());
            reportList.forEach(report -> stringBuilder.append(" - ").append(report).append(System.lineSeparator()));
            stringBuilder.append(System.lineSeparator());
        });
        return stringBuilder.toString();
    }

    /**
     * Prints the reports and fails the test if there are any
     *
     * @param classReports  The class reports
     * @param methodReports The method reports
     */
    private void printReports(Map<Class<?>, Set<String>> classReports, Map<Method, Set<String>> methodReports) {
        // take only distinct reports and add them to the class reports
        methodReports.forEach((methodKey, value) -> classReports.computeIfAbsent(methodKey.getDeclaringClass(), key -> new HashSet<>()).addAll(value));

        // Print the reports and fail the tests if there are any. Otherwise, the test succeeds
        if (!classReports.isEmpty()) {
            fail(formatReportString(classReports));
        }
    }
}
