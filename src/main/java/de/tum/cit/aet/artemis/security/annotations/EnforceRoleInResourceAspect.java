package de.tum.cit.aet.artemis.security.annotations;

import static de.tum.cit.aet.artemis.security.annotations.AnnotationUtils.getAnnotation;
import static de.tum.cit.aet.artemis.security.annotations.AnnotationUtils.getIdFromSignature;
import static de.tum.cit.aet.artemis.security.annotations.AnnotationUtils.getValue;

import java.lang.annotation.Annotation;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;

import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;

public abstract class EnforceRoleInResourceAspect {

    protected final AuthorizationCheckService authorizationCheckService;

    private final Class<? extends Annotation> genericAnnotationClass;

    private final Class<? extends Annotation> studentAnnotationClass;

    private final Class<? extends Annotation> tutorAnnotationClass;

    private final Class<? extends Annotation> editorAnnotationClass;

    private final Class<? extends Annotation> instructorAnnotationClass;

    protected EnforceRoleInResourceAspect(AuthorizationCheckService authorizationCheckService, Class<? extends Annotation> genericAnnotationClass,
            Class<? extends Annotation> studentAnnotationClass, Class<? extends Annotation> tutorAnnotationClass, Class<? extends Annotation> editorAnnotationClass,
            Class<? extends Annotation> instructorAnnotationClass) {
        this.authorizationCheckService = authorizationCheckService;
        this.genericAnnotationClass = genericAnnotationClass;
        this.studentAnnotationClass = studentAnnotationClass;
        this.tutorAnnotationClass = tutorAnnotationClass;
        this.editorAnnotationClass = editorAnnotationClass;
        this.instructorAnnotationClass = instructorAnnotationClass;
    }

    /**
     * Pointcut around all methods or classes annotated with EnforceRoleInResource.
     */
    @Pointcut
    protected abstract void callAt();

    protected abstract void authorizationCheck(Role role, long resourceId);

    private static <T extends Annotation> Role getRole(T annotation) {
        return getValue(annotation, "value", Role.class).orElseThrow(() -> new IllegalStateException("Role value is missing in the annotation."));
    }

    private static <T extends Annotation> String getResourceIdFieldName(T annotation) {
        return getValue(annotation, "resourceIdFieldName", String.class).orElseThrow(() -> new IllegalStateException("Resource ID field name is missing in the annotation."));
    }

    private <T extends Annotation> String getResourceIdFieldName(T genericAnnotation, Role role, ProceedingJoinPoint joinPoint) {
        final var defaultFieldName = getResourceIdFieldName(genericAnnotation);

        Optional<? extends Annotation> simpleAnnotation = switch (role) {
            case INSTRUCTOR -> getAnnotation(instructorAnnotationClass, joinPoint);
            case EDITOR -> getAnnotation(editorAnnotationClass, joinPoint);
            case TEACHING_ASSISTANT -> getAnnotation(tutorAnnotationClass, joinPoint);
            case STUDENT -> getAnnotation(studentAnnotationClass, joinPoint);
            default -> Optional.empty();
        };
        if (simpleAnnotation.isPresent()) {
            return getResourceIdFieldName(simpleAnnotation.get());
        }
        return defaultFieldName;
    }

    /**
     * Aspect around all methods for which a role in resource has been activated. Will check if the user has the required role in the resource and only execute the underlying
     * method if the user has the required role. Will otherwise return forbidden (as response entity)
     *
     * @param joinPoint Proceeding join point of the aspect
     * @return The original return value of the called method, if the authorization checks pass, a forbidden response entity otherwise
     * @throws Throwable If there was any error during method execution (both the aspect or the actual called method)
     */
    @Around(value = "callAt()", argNames = "joinPoint")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        final var genericAnnotation = getAnnotation(genericAnnotationClass, joinPoint).orElseThrow();
        final var role = getRole(genericAnnotation);
        final var resourceIdFieldName = getResourceIdFieldName(genericAnnotation, role, joinPoint);
        final var resourceId = getIdFromSignature(joinPoint, resourceIdFieldName).orElseThrow(() -> new IllegalArgumentException(
                "Method annotated with an EnforceRoleInResource annotation must have a parameter named " + resourceIdFieldName + " of type long/Long."));
        authorizationCheck(role, resourceId);
        return joinPoint.proceed();
    }
}
