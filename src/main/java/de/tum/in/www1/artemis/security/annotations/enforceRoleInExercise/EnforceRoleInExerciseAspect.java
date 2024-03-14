package de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.security.annotations.AnnotationUtils.getAnnotation;
import static de.tum.in.www1.artemis.security.annotations.AnnotationUtils.getIdFromSignature;

import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
@Component
@Aspect
public class EnforceRoleInExerciseAspect {

    private final AuthorizationCheckService authorizationCheckService;

    public EnforceRoleInExerciseAspect(AuthorizationCheckService authorizationCheckService) {
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInExercise}.
     */
    @Pointcut("@within(EnforceRoleInExercise) || @annotation(EnforceRoleInExercise) || execution(@(@EnforceRoleInExercise *) * *(..))")
    public void callAt() {
    }

    /**
     * Aspect around all methods for which a role in exercise has been activated. Will check if the user has the required role in the exercise and only execute the underlying
     * method if
     * the user has the required role. Will otherwise return forbidden (as response entity)
     *
     * @param joinPoint Proceeding join point of the aspect
     * @return The original return value of the called method, if all features are enabled, a forbidden response entity otherwise
     * @throws Throwable If there was any error during method execution (both the aspect or the actual called method)
     */
    @Around(value = "callAt()", argNames = "joinPoint")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        final var annotation = getAnnotation(EnforceRoleInExercise.class, joinPoint).orElseThrow();
        final var exerciseIdFieldName = getExerciseIdFieldName(annotation, joinPoint);
        final var exerciseId = getIdFromSignature(joinPoint, exerciseIdFieldName).orElseThrow(() -> new IllegalArgumentException(
                "Method annotated with @EnforceRoleInExercise must have a parameter named " + annotation.exerciseIdFieldName() + " of type long/Long."));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(annotation.value(), exerciseId);
        return joinPoint.proceed();
    }

    /**
     * Extracts the exerciseIdFieldName from the annotation or the method arguments
     *
     * @param annotation the annotation
     * @param joinPoint  the join point
     * @return the exerciseIdFieldName
     */
    private String getExerciseIdFieldName(EnforceRoleInExercise annotation, ProceedingJoinPoint joinPoint) {
        final var defaultFieldName = annotation.exerciseIdFieldName();
        Optional<String> fieldNameOfSimpleAnnotation = switch (annotation.value()) {
            case INSTRUCTOR -> getAnnotation(EnforceAtLeastInstructorInExercise.class, joinPoint).map(EnforceAtLeastInstructorInExercise::exerciseIdFieldName);
            case EDITOR -> getAnnotation(EnforceAtLeastEditorInExercise.class, joinPoint).map(EnforceAtLeastEditorInExercise::exerciseIdFieldName);
            case TEACHING_ASSISTANT -> getAnnotation(EnforceAtLeastTutorInExercise.class, joinPoint).map(EnforceAtLeastTutorInExercise::exerciseIdFieldName);
            case STUDENT -> getAnnotation(EnforceAtLeastStudentInExercise.class, joinPoint).map(EnforceAtLeastStudentInExercise::exerciseIdFieldName);
            default -> Optional.empty();
        };
        return fieldNameOfSimpleAnnotation.orElse(defaultFieldName);
    }
}
