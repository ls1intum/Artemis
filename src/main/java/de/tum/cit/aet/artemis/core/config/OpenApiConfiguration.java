package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Configuration for OpenAPI (Swagger) documentation generation.
 * <p>
 * This configuration ensures that endpoints secured with custom authorization annotations
 * (like {@link EnforceAtLeastInstructorInCourse}) are properly documented in the generated OpenAPI specification.
 */
@Configuration
@Profile(PROFILE_CORE)
@SecurityScheme(name = "bearer-jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {

    /**
     * Customizes OpenAPI operations to include security requirements based on custom annotations.
     * <p>
     * This customizer detects both standard Spring Security {@link PreAuthorize} annotations
     * and custom authorization annotations (e.g., {@link EnforceAtLeastInstructorInCourse})
     * to properly document the security requirements for each endpoint.
     *
     * @return an OperationCustomizer that adds security information to API endpoints
     */
    @Bean
    public OperationCustomizer customSecurityAnnotationCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            Method method = handlerMethod.getMethod();

            // Extract security requirements from annotations
            List<String> securityDescriptions = extractSecurityDescriptions(method);

            if (!securityDescriptions.isEmpty()) {
                // Add bearer-jwt security requirement
                SecurityRequirement securityRequirement = new SecurityRequirement().addList("bearer-jwt");
                operation.addSecurityItem(securityRequirement);

                // Add description to the operation
                String currentDescription = operation.getDescription() != null ? operation.getDescription() : "";
                String securityInfo = String.join(", ", securityDescriptions);
                String enhancedDescription = currentDescription.isEmpty() ? "**Security:** " + securityInfo : currentDescription + "\n\n**Security:** " + securityInfo;
                operation.setDescription(enhancedDescription);
            }

            return operation;
        };
    }

    /**
     * Extracts security requirement descriptions from method annotations.
     *
     * @param method the method to analyze
     * @return a list of human-readable security requirement descriptions
     */
    private List<String> extractSecurityDescriptions(Method method) {
        List<String> descriptions = new ArrayList<>();
        String docDescription = null;

        // Check for custom role-based annotations (global)
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAdmin.class)) {
            descriptions.add("Requires ADMIN role");
            docDescription = extractDocDescription(method, EnforceAdmin.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastInstructor.class)) {
            descriptions.add("Requires at least INSTRUCTOR role");
            docDescription = extractDocDescription(method, EnforceAtLeastInstructor.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastEditor.class)) {
            descriptions.add("Requires at least EDITOR role");
            docDescription = extractDocDescription(method, EnforceAtLeastEditor.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastTutor.class)) {
            descriptions.add("Requires at least TUTOR (TA) role");
            docDescription = extractDocDescription(method, EnforceAtLeastTutor.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastStudent.class)) {
            descriptions.add("Requires at least STUDENT role");
            docDescription = extractDocDescription(method, EnforceAtLeastStudent.class);
        }

        // Check for custom course-scoped annotations
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastInstructorInCourse.class)) {
            descriptions.add("Requires at least INSTRUCTOR role in the course");
            docDescription = extractDocDescription(method, EnforceAtLeastInstructorInCourse.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastEditorInCourse.class)) {
            descriptions.add("Requires at least EDITOR role in the course");
            docDescription = extractDocDescription(method, EnforceAtLeastEditorInCourse.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastTutorInCourse.class)) {
            descriptions.add("Requires at least TUTOR (TA) role in the course");
            docDescription = extractDocDescription(method, EnforceAtLeastTutorInCourse.class);
        }
        if (AnnotatedElementUtils.hasAnnotation(method, EnforceAtLeastStudentInCourse.class)) {
            descriptions.add("Requires at least STUDENT role in the course");
            docDescription = extractDocDescription(method, EnforceAtLeastStudentInCourse.class);
        }

        // Check for similar exercise-scoped annotations
        if (hasAnnotation(method, "EnforceAtLeastInstructorInExercise")) {
            descriptions.add("Requires at least INSTRUCTOR role in the exercise's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastInstructorInExercise");
        }
        if (hasAnnotation(method, "EnforceAtLeastEditorInExercise")) {
            descriptions.add("Requires at least EDITOR role in the exercise's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastEditorInExercise");
        }
        if (hasAnnotation(method, "EnforceAtLeastTutorInExercise")) {
            descriptions.add("Requires at least TUTOR (TA) role in the exercise's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastTutorInExercise");
        }
        if (hasAnnotation(method, "EnforceAtLeastStudentInExercise")) {
            descriptions.add("Requires at least STUDENT role in the exercise's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastStudentInExercise");
        }

        // Check for similar lecture-scoped annotations
        if (hasAnnotation(method, "EnforceAtLeastInstructorInLecture")) {
            descriptions.add("Requires at least INSTRUCTOR role in the lecture's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastInstructorInLecture");
        }
        if (hasAnnotation(method, "EnforceAtLeastEditorInLecture")) {
            descriptions.add("Requires at least EDITOR role in the lecture's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastEditorInLecture");
        }
        if (hasAnnotation(method, "EnforceAtLeastTutorInLecture")) {
            descriptions.add("Requires at least TUTOR (TA) role in the lecture's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastTutorInLecture");
        }
        if (hasAnnotation(method, "EnforceAtLeastStudentInLecture")) {
            descriptions.add("Requires at least STUDENT role in the lecture's course");
            docDescription = extractDocDescriptionByName(method, "EnforceAtLeastStudentInLecture");
        }

        // Add docDescription as a feature description if present
        if (docDescription != null && !docDescription.isEmpty()) {
            descriptions.add("Feature: " + docDescription);
        }

        // Check for PreAuthorize as fallback
        PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (preAuthorize != null && descriptions.isEmpty()) {
            // Only add PreAuthorize info if no custom annotation was found
            String expression = preAuthorize.value();
            descriptions.add("Requires authorization: " + expression);
        }

        return descriptions;
    }

    /**
     * Checks if a method has an annotation with the given simple class name.
     * This is a helper method to check for annotations without needing direct class references,
     * which is useful for annotations in other modules that may not be available at compile time.
     *
     * @param method              the method to check
     * @param annotationClassName the simple class name of the annotation (without package)
     * @return true if the annotation is present, false otherwise
     */
    private boolean hasAnnotation(Method method, String annotationClassName) {
        return Arrays.stream(method.getAnnotations()).anyMatch(annotation -> annotation.annotationType().getSimpleName().equals(annotationClassName));
    }

    /**
     * Extracts the docDescription field from an annotation if present.
     *
     * @param method          the method with the annotation
     * @param annotationClass the annotation class
     * @return the docDescription value, or null if not present or empty
     */
    private String extractDocDescription(Method method, Class<? extends Annotation> annotationClass) {
        try {
            Annotation annotation = method.getAnnotation(annotationClass);
            if (annotation != null) {
                Method docDescMethod = annotationClass.getMethod("docDescription");
                String value = (String) docDescMethod.invoke(annotation);
                return (value != null && !value.isEmpty()) ? value : null;
            }
        }
        catch (Exception e) {
            // If docDescription field doesn't exist or can't be accessed, return null
        }
        return null;
    }

    /**
     * Extracts the docDescription field from an annotation by its simple class name.
     *
     * @param method              the method with the annotation
     * @param annotationClassName the simple class name of the annotation
     * @return the docDescription value, or null if not present or empty
     */
    private String extractDocDescriptionByName(Method method, String annotationClassName) {
        try {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation.annotationType().getSimpleName().equals(annotationClassName)) {
                    Method docDescMethod = annotation.annotationType().getMethod("docDescription");
                    String value = (String) docDescMethod.invoke(annotation);
                    return (value != null && !value.isEmpty()) ? value : null;
                }
            }
        }
        catch (Exception e) {
            // If docDescription field doesn't exist or can't be accessed, return null
        }
        return null;
    }
}
