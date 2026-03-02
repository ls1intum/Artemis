package de.tum.cit.aet.artemis.core.security.policy.docs;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.Conditions;

/**
 * Scans the codebase for REST controller methods with custom authorization annotations
 * (like {@code @EnforceAtLeastInstructorInCourse}) and creates synthetic access policies
 * for documentation generation.
 */
public final class AnnotationBasedPolicyCollector {

    private AnnotationBasedPolicyCollector() {
    }

    /**
     * Represents metadata about an endpoint secured with a custom annotation.
     */
    record EndpointInfo(String featureName, String section, Role minimumRole, String note, String path, String httpMethod) {
    }

    /**
     * Collects all endpoints with custom authorization annotations and returns them as synthetic access policies.
     *
     * @return list of synthetic access policies representing annotation-based endpoints
     */
    public static List<AccessPolicy<?>> collectAnnotationBasedPolicies() {
        List<EndpointInfo> endpoints = scanForAnnotatedEndpoints();
        List<AccessPolicy<?>> policies = new ArrayList<>();

        for (EndpointInfo endpoint : endpoints) {
            AccessPolicy<Object> policy = createSyntheticPolicy(endpoint);
            policies.add(policy);
        }

        return policies;
    }

    /**
     * Scans for all REST controller methods with custom authorization annotations.
     * <p>
     * This uses filesystem-based scanning to avoid Spring's conditional evaluation issues.
     *
     * @return list of endpoint information
     */
    private static List<EndpointInfo> scanForAnnotatedEndpoints() {
        List<EndpointInfo> endpoints = new ArrayList<>();

        // Scan the web packages for REST controllers using filesystem scanning
        String[] packagesToScan = { "de.tum.cit.aet.artemis.core.web", "de.tum.cit.aet.artemis.assessment.web", "de.tum.cit.aet.artemis.atlas.web",
                "de.tum.cit.aet.artemis.athena.web", "de.tum.cit.aet.artemis.communication.web", "de.tum.cit.aet.artemis.exam.web", "de.tum.cit.aet.artemis.exercise.web",
                "de.tum.cit.aet.artemis.fileupload.web", "de.tum.cit.aet.artemis.iris.web", "de.tum.cit.aet.artemis.lecture.web", "de.tum.cit.aet.artemis.lti.web",
                "de.tum.cit.aet.artemis.modeling.web", "de.tum.cit.aet.artemis.plagiarism.web", "de.tum.cit.aet.artemis.programming.web", "de.tum.cit.aet.artemis.quiz.web",
                "de.tum.cit.aet.artemis.text.web", "de.tum.cit.aet.artemis.tutorialgroup.web" };

        for (String packageName : packagesToScan) {
            scanPackageForControllers(packageName, endpoints);
        }

        return endpoints;
    }

    /**
     * Scans a specific package for REST controllers using filesystem scanning.
     *
     * @param packageName the package to scan
     * @param endpoints   the list to add found endpoints to
     */
    private static void scanPackageForControllers(String packageName, List<EndpointInfo> endpoints) {
        try {
            // Convert package name to resource path
            String packagePath = packageName.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(packagePath);

            if (resource == null) {
                System.err.println("Warning: Package not found: " + packageName);
                return;
            }

            File packageDir = new File(resource.getFile());
            if (!packageDir.exists() || !packageDir.isDirectory()) {
                System.err.println("Warning: Not a directory: " + packageDir);
                return;
            }

            // Recursively scan for .class files
            scanDirectory(packageDir, packageName, endpoints);
        }
        catch (Exception e) {
            System.err.println("Warning: Error scanning package " + packageName + ": " + e.getMessage());
        }
    }

    /**
     * Recursively scans a directory for .class files and processes REST controllers.
     *
     * @param dir         the directory to scan
     * @param packageName the package name corresponding to this directory
     * @param endpoints   the list to add found endpoints to
     */
    private static void scanDirectory(File dir, String packageName, List<EndpointInfo> endpoints) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                scanDirectory(file, packageName + "." + file.getName(), endpoints);
            }
            else if (file.getName().endsWith(".class")) {
                // Process class file
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                processClass(className, endpoints);
            }
        }
    }

    /**
     * Processes a single class file, checking if it's a REST controller with custom annotations.
     *
     * @param className the fully qualified class name
     * @param endpoints the list to add found endpoints to
     */
    private static void processClass(String className, List<EndpointInfo> endpoints) {
        try {
            Class<?> clazz = Class.forName(className);

            // Check if it's a REST controller
            if (!clazz.isAnnotationPresent(RestController.class)) {
                return;
            }

            // Process all methods in this controller
            for (Method method : clazz.getDeclaredMethods()) {
                EndpointInfo info = extractEndpointInfo(clazz, method);
                if (info != null) {
                    endpoints.add(info);
                }
            }
        }
        catch (ClassNotFoundException e) {
            // Skip classes that cannot be loaded
            System.err.println("Warning: Could not load class " + className);
        }
        catch (NoClassDefFoundError e) {
            // Skip classes with missing dependencies
            System.err.println("Warning: Missing dependency for class " + className);
        }
        catch (Exception e) {
            // Skip classes that cause other errors
            System.err.println("Warning: Error processing class " + className + ": " + e.getMessage());
        }
    }

    /**
     * Extracts endpoint information from a method, if it has relevant custom annotations.
     *
     * @param controller the controller class
     * @param method     the method to analyze
     * @return endpoint info if the method has custom authorization annotations, null otherwise
     */
    private static EndpointInfo extractEndpointInfo(Class<?> controller, Method method) {
        // Check for custom authorization annotations and extract role requirement
        Role minimumRole = null;
        String note = null;

        // Course-scoped annotations
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastInstructorInCourse.class)) {
            minimumRole = Role.INSTRUCTOR;
            note = "if in course";
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastEditorInCourse.class)) {
            minimumRole = Role.EDITOR;
            note = "if in course";
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastTutorInCourse.class)) {
            minimumRole = Role.TEACHING_ASSISTANT;
            note = "if in course";
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastStudentInCourse.class)) {
            minimumRole = Role.STUDENT;
            note = "if in course";
        }
        // Check for exercise-scoped annotations
        else if (hasAnnotation(method, "EnforceAtLeastInstructorInExercise")) {
            minimumRole = Role.INSTRUCTOR;
            note = "if in course";
        }
        else if (hasAnnotation(method, "EnforceAtLeastEditorInExercise")) {
            minimumRole = Role.EDITOR;
            note = "if in course";
        }
        else if (hasAnnotation(method, "EnforceAtLeastTutorInExercise")) {
            minimumRole = Role.TEACHING_ASSISTANT;
            note = "if in course";
        }
        else if (hasAnnotation(method, "EnforceAtLeastStudentInExercise")) {
            minimumRole = Role.STUDENT;
            note = "if in course";
        }
        // Check for lecture-scoped annotations
        else if (hasAnnotation(method, "EnforceAtLeastInstructorInLecture")) {
            minimumRole = Role.INSTRUCTOR;
            note = "if in course";
        }
        else if (hasAnnotation(method, "EnforceAtLeastEditorInLecture")) {
            minimumRole = Role.EDITOR;
            note = "if in course";
        }
        else if (hasAnnotation(method, "EnforceAtLeastTutorInLecture")) {
            minimumRole = Role.TEACHING_ASSISTANT;
            note = "if in course";
        }
        else if (hasAnnotation(method, "EnforceAtLeastStudentInLecture")) {
            minimumRole = Role.STUDENT;
            note = "if in course";
        }
        // Global role annotations
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAdmin.class)) {
            minimumRole = Role.ADMIN;
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastInstructor.class)) {
            minimumRole = Role.INSTRUCTOR;
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastEditor.class)) {
            minimumRole = Role.EDITOR;
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastTutor.class)) {
            minimumRole = Role.TEACHING_ASSISTANT;
        }
        else if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastStudent.class)) {
            minimumRole = Role.STUDENT;
        }

        // If no custom annotation found, skip this method
        if (minimumRole == null) {
            return null;
        }

        // Extract feature name from method name and HTTP mapping
        String featureName = generateFeatureName(method);
        String section = determineSectionFromController(controller);
        String path = extractPathFromMethod(controller, method);
        String httpMethod = extractHttpMethod(method);

        return new EndpointInfo(featureName, section, minimumRole, note, path, httpMethod);
    }

    /**
     * Checks if a method has an annotation with the given simple class name.
     *
     * @param method              the method to check
     * @param annotationClassName the simple class name of the annotation
     * @return true if the annotation is present, false otherwise
     */
    private static boolean hasAnnotation(Method method, String annotationClassName) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(annotationClassName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates a human-readable feature name from the method.
     *
     * @param method the method
     * @return a feature name
     */
    private static String generateFeatureName(Method method) {
        // Convert method name from camelCase to words
        String methodName = method.getName();

        // Common REST method prefixes to remove
        methodName = methodName.replaceFirst("^(get|post|put|delete|patch|create|update|remove|add|search|view|list|download|archive|cleanup|import|export)", "");

        // Insert spaces before capital letters
        String withSpaces = methodName.replaceAll("([A-Z])", " $1").trim();

        // Capitalize first letter
        if (!withSpaces.isEmpty()) {
            withSpaces = Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
        }

        return withSpaces.isEmpty() ? methodName : withSpaces;
    }

    /**
     * Determines the documentation section based on the controller class name.
     *
     * @param controller the controller class
     * @return the section name
     */
    private static String determineSectionFromController(Class<?> controller) {
        String className = controller.getSimpleName();

        // Map controller names to documentation sections
        if (className.contains("Course") && !className.contains("Exercise")) {
            return "Course Access";
        }
        else if (className.contains("ProgrammingExercise")) {
            return "ProgrammingExercises";
        }
        else if (className.contains("Exercise")) {
            return "Exercises";
        }
        else if (className.contains("Lecture")) {
            return "Lectures";
        }
        else if (className.contains("Exam")) {
            return "Exams";
        }
        else if (className.contains("Navigation") || className.contains("Overview")) {
            return "Navigation";
        }

        // Default section
        return "Other";
    }

    /**
     * Extracts the request path from the method's mapping annotations.
     *
     * @param controller the controller class
     * @param method     the method
     * @return the path or empty string if not found
     */
    private static String extractPathFromMethod(Class<?> controller, Method method) {
        // Get base path from controller
        RequestMapping controllerMapping = controller.getAnnotation(RequestMapping.class);
        String basePath = (controllerMapping != null && controllerMapping.value().length > 0) ? controllerMapping.value()[0] : "";

        // Get method path
        String methodPath = "";
        if (method.isAnnotationPresent(GetMapping.class)) {
            methodPath = method.getAnnotation(GetMapping.class).value().length > 0 ? method.getAnnotation(GetMapping.class).value()[0] : "";
        }
        else if (method.isAnnotationPresent(PostMapping.class)) {
            methodPath = method.getAnnotation(PostMapping.class).value().length > 0 ? method.getAnnotation(PostMapping.class).value()[0] : "";
        }
        else if (method.isAnnotationPresent(PutMapping.class)) {
            methodPath = method.getAnnotation(PutMapping.class).value().length > 0 ? method.getAnnotation(PutMapping.class).value()[0] : "";
        }
        else if (method.isAnnotationPresent(DeleteMapping.class)) {
            methodPath = method.getAnnotation(DeleteMapping.class).value().length > 0 ? method.getAnnotation(DeleteMapping.class).value()[0] : "";
        }
        else if (method.isAnnotationPresent(PatchMapping.class)) {
            methodPath = method.getAnnotation(PatchMapping.class).value().length > 0 ? method.getAnnotation(PatchMapping.class).value()[0] : "";
        }

        return basePath + methodPath;
    }

    /**
     * Extracts the HTTP method from the method's mapping annotations.
     *
     * @param method the method
     * @return the HTTP method (GET, POST, etc.) or empty string
     */
    private static String extractHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class))
            return "GET";
        if (method.isAnnotationPresent(PostMapping.class))
            return "POST";
        if (method.isAnnotationPresent(PutMapping.class))
            return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class))
            return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class))
            return "PATCH";
        return "";
    }

    /**
     * Creates a synthetic access policy from endpoint information.
     *
     * @param endpoint the endpoint info
     * @return a synthetic access policy
     */
    private static AccessPolicy<Object> createSyntheticPolicy(EndpointInfo endpoint) {
        // Build the policy with documentation metadata
        var builder = AccessPolicy.forResource(Object.class).named("annotation-" + endpoint.featureName().toLowerCase().replace(" ", "-")).section(endpoint.section())
                .feature(endpoint.featureName());

        // Add allow rule for the minimum role and all higher roles
        List<Role> allowedRoles = getRolesAtLevel(endpoint.minimumRole());

        // Build the rule using the when() helper from AccessPolicy
        var ruleBuilder = AccessPolicy.when(Conditions.always()).thenAllow().documentedFor(allowedRoles.toArray(new Role[0]));

        // Add note if present
        if (endpoint.note() != null) {
            builder.rule(ruleBuilder.withNote(endpoint.note()));
        }
        else {
            builder.rule(ruleBuilder);
        }

        return builder.denyByDefault();
    }

    /**
     * Gets all roles at or above the specified minimum role level.
     *
     * @param minimumRole the minimum required role
     * @return list of allowed roles
     */
    private static List<Role> getRolesAtLevel(Role minimumRole) {
        List<Role> roles = new ArrayList<>();

        // Always include admin roles
        roles.add(Role.SUPER_ADMIN);
        roles.add(Role.ADMIN);

        // Add course-specific roles based on hierarchy
        switch (minimumRole) {
            case STUDENT:
                roles.add(Role.STUDENT);
                // fall through
            case TEACHING_ASSISTANT:
                roles.add(Role.TEACHING_ASSISTANT);
                // fall through
            case EDITOR:
                roles.add(Role.EDITOR);
                // fall through
            case INSTRUCTOR:
                roles.add(Role.INSTRUCTOR);
                break;
            case ADMIN:
                // Admin only
                break;
            case SUPER_ADMIN:
                // Super Admin only
                break;
            default:
                break;
        }

        return roles;
    }
}
