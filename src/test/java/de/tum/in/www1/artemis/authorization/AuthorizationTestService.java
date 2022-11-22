package de.tum.in.www1.artemis.authorization;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;

@Service
public class AuthorizationTestService {

    private static final List<Class<? extends Annotation>> ALLOWED_AUTH_METHOD_ANNOTATIONS = List.of(EnforceAdmin.class, EnforceNothing.class, PreAuthorize.class);

    private final Method preAuthorizeValueAnnotation = PreAuthorize.class.getDeclaredMethod("value");

    public AuthorizationTestService() throws NoSuchMethodException {
        // Empty constructor to add exception
    }

    /**
     * Returns all annotations on the given method that are relevant to authorization
     *
     * @param method the method to check
     * @return List of relevant annotations
     */
    public List<Annotation> getAuthAnnotations(Method method) {
        var annotations = Arrays.asList(method.getAnnotations());
        return annotations.stream().filter(annotation -> ALLOWED_AUTH_METHOD_ANNOTATIONS.contains(annotation.annotationType())).toList();
    }

    /**
     * Returns all annotations on the given class that are relevant to authorization.
     *
     * @param clazz the class to check
     * @return List of relevant annotations
     */
    public List<Annotation> getAuthAnnotations(Class<?> clazz) {
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
    public <T> void addElement(Map<T, Set<String>> reportsMap, T reportObject, String report) {
        if (reportsMap.containsKey(reportObject)) {
            reportsMap.get(reportObject).add(report);
        }
        else {
            reportsMap.put(reportObject, new HashSet<>(Set.of(report)));
        }
    }

    /**
     * Creates a formatted string representation of the given reports
     * @param reportsMap The reports to format
     * @return The formatted string
     */
    public String formatReportString(Map<Class<?>, Set<String>> reportsMap) {
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
     * @param classAnnotation The annotation to check
     * @param javaClass The class the annotation is on
     * @param javaMethod The method of the endpoint currently in scope
     * @param classReports The current collection of reports
     */
    public void checkClassAnnotation(Annotation classAnnotation, Class<?> javaClass, Method javaMethod, Map<Class<?>, Set<String>> classReports)
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
     * @param annotation The annotation to check
     * @param javaMethod The method the annotation is on
     * @param methodReports The current collection of reports
     */
    public void checkMethodAnnotation(Annotation annotation, Method javaMethod, Map<Method, Set<String>> methodReports) throws InvocationTargetException, IllegalAccessException {
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
}
