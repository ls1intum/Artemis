package de.tum.in.www1.artemis.authorization;

import static org.assertj.core.api.Fail.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.security.annotations.ManualConfig;

/**
 * This service is used to check if the authorization annotations are used correctly.
 */
@Service
public class AuthorizationTestService {

    private static final Set<Class<? extends Annotation>> ALLOWED_AUTH_METHOD_ANNOTATIONS = Set.of(EnforceAdmin.class, EnforceNothing.class, PreAuthorize.class);

    private final Method preAuthorizeValueAnnotation = PreAuthorize.class.getDeclaredMethod("value");

    public AuthorizationTestService() throws NoSuchMethodException {
        // Empty constructor to add exception
    }

    /**
     * Tests all endpoints and prints the reports
     *
     * @param endpointMap The map of all endpoints
     */
    public void testEndpoints(Map<RequestMappingInfo, HandlerMethod> endpointMap) throws InvocationTargetException, IllegalAccessException {
        Map<Class<?>, Set<String>> classReports = new HashMap<>();
        Map<Method, Set<String>> methodReports = new HashMap<>();

        // Test each endpoint and collect the reports
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : endpointMap.entrySet()) {
            testEndpoint(entry.getValue(), classReports, methodReports);
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
        return handlerMethod.getMethod().getAnnotation(Profile.class) != null || handlerMethod.getMethod().getDeclaringClass().getAnnotation(Profile.class) != null;
    }

    /**
     * Tests a single endpoint and collects the reports
     * Additional tests should be added here.
     *
     * @param method        The handler method of the endpoint
     * @param classReports  The current class reports
     * @param methodReports The current method reports
     */
    private void testEndpoint(HandlerMethod method, Map<Class<?>, Set<String>> classReports, Map<Method, Set<String>> methodReports)
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
        List<Annotation> methodAnnotations = getAuthAnnotations(javaMethod);
        List<Annotation> classAnnotations = getAuthAnnotations(javaClass);

        // Cover edge cases: No annotation, Multiple method annotations, Multiple class annotations
        if (methodAnnotations.isEmpty() && classAnnotations.isEmpty()) {
            addElement(methodReports, javaMethod, "No authorization annotation found for " + javaMethod.getName() + "().");
        }
        if (methodAnnotations.size() > 1) {
            addElement(methodReports, javaMethod, "Multiple method authorization annotations found for " + javaMethod.getName() + "().");
        }
        if (classAnnotations.size() > 1) {
            addElement(classReports, javaClass, "Multiple class authorization annotations found.");
        }

        // Cover default cases: One class annotation, one method annotation, mixture of class and method annotations
        // The mixture case gets executed in addition to the edge cases to give the developer all information at once
        if (classAnnotations.size() == 1 && methodAnnotations.isEmpty()) {
            checkClassAnnotation(classAnnotations.get(0), javaClass, javaMethod, classReports);
        }
        else if (methodAnnotations.size() == 1 && classAnnotations.isEmpty()) {
            Annotation annotation = methodAnnotations.get(0);
            if (annotation.annotationType().equals(PreAuthorize.class)) {
                checkMethodAnnotation(annotation, javaMethod, methodReports);
            }
        }
        else if (!methodAnnotations.isEmpty() && !classAnnotations.isEmpty()) {
            // Collision between class and method annotations
            addElement(methodReports, javaMethod,
                    "Collision between class and method authorization annotations found for " + javaMethod.getName() + "(). Collisions should be " + "avoided" + ".");
        }
    }

    /**
     * Returns all annotations on the given method that are relevant to authorization
     *
     * @param method the method to check
     * @return List of relevant annotations
     */
    private List<Annotation> getAuthAnnotations(Method method) {
        var annotations = Arrays.asList(method.getAnnotations());
        return annotations.stream().filter(annotation -> ALLOWED_AUTH_METHOD_ANNOTATIONS.contains(annotation.annotationType())).toList();
    }

    /**
     * Returns all annotations on the given class that are relevant to authorization.
     *
     * @param clazz the class to check
     * @return List of relevant annotations
     */
    private List<Annotation> getAuthAnnotations(Class<?> clazz) {
        var annotations = Arrays.asList(clazz.getAnnotations());
        return annotations.stream().filter(annotation -> annotation.annotationType().equals(PreAuthorize.class)).toList();
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
            reportList.forEach(report -> {
                stringBuilder.append(" - ").append(report).append(System.lineSeparator());
            });
            stringBuilder.append(System.lineSeparator());
        });
        return stringBuilder.toString();
    }

    /**
     * Checks if the given pre-authorization annotation on a class element is valid
     *
     * @param classAnnotation The annotation to check
     * @param javaClass       The class the annotation is on
     * @param javaMethod      The method of the endpoint currently in scope
     * @param classReports    The current collection of reports
     */
    private void checkClassAnnotation(Annotation classAnnotation, Class<?> javaClass, Method javaMethod, Map<Class<?>, Set<String>> classReports)
            throws InvocationTargetException, IllegalAccessException {
        // check class
        switch (getValueOfAnnotation(classAnnotation)) {
            case "hasRole('ADMIN')":
                addElement(classReports, javaClass,
                        "PreAuthorize(\"hasRole('Admin')\") class annotation found for " + javaMethod.getName() + "() but not allowed. Use @EnforceAdmin for methods instead.");
                break;
            case "hasRole('INSTRUCTOR')":
            case "hasRole('EDITOR')":
            case "hasRole('TA')":
            case "hasRole('USER')":
                break;
            case "permitAll()":
                addElement(classReports, javaClass,
                        "PreAuthorize(\"permitAll\") class annotation found for " + javaMethod.getName() + "() but not allowed. Use @EnforceNothing for methods instead.");
                break;
            default:
                addElement(classReports, javaClass, "PreAuthorize class annotation with unknown content found for " + javaMethod.getName() + "().");
        }
    }

    /**
     * Checks if the given pre-authorization annotation on a method element is valid
     *
     * @param annotation    The annotation to check
     * @param javaMethod    The method the annotation is on
     * @param methodReports The current collection of reports
     */
    private void checkMethodAnnotation(Annotation annotation, Method javaMethod, Map<Method, Set<String>> methodReports) throws InvocationTargetException, IllegalAccessException {
        switch (getValueOfAnnotation(annotation)) {
            case "hasRole('ADMIN')":
                addElement(methodReports, javaMethod,
                        "PreAuthorize(\"hasRole('Admin')\") method annotation found for " + javaMethod.getName() + "() but not allowed. Use @EnforceAdmin instead.");
                break;
            case "hasRole('INSTRUCTOR')":
            case "hasRole('EDITOR')":
            case "hasRole('TA')":
            case "hasRole('USER')":
                break;
            case "permitAll()":
                addElement(methodReports, javaMethod,
                        "PreAuthorize(\"permitAll\") method annotation found for " + javaMethod.getName() + "() but not allowed. Use @EnforceNothing instead.");
                break;
            default:
                addElement(methodReports, javaMethod, "PreAuthorize method annotation with unknown content found for " + javaMethod.getName() + "().");
        }
    }

    /**
     * Access a given annotation object and return the value of the annotation.
     *
     * @param annotation the annotation to access
     * @return The value of the annotation
     * @throws InvocationTargetException if the annotation does not have a value method
     */
    private String getValueOfAnnotation(Annotation annotation) throws InvocationTargetException, IllegalAccessException {
        return (String) preAuthorizeValueAnnotation.invoke(annotation);
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
