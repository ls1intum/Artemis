package de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.AuthorizationCheckService;

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
        final var annotation = getAnnotation(joinPoint);
        final var exerciseId = getExerciseId(joinPoint, annotation).orElseThrow(() -> new IllegalArgumentException(
                "Method annotated with @EnforceRoleInExercise must have a parameter named " + annotation.exerciseIdFieldName() + " of type long/Long."));
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(annotation.value(), exerciseId);
        return joinPoint.proceed();
    }

    /**
     * Extracts the {@link EnforceRoleInExercise} annotation from the method or type
     *
     * @param joinPoint the join point
     * @return the annotation if it is present, null otherwise
     */
    private EnforceRoleInExercise getAnnotation(ProceedingJoinPoint joinPoint) {
        var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        EnforceRoleInExercise annotation = method.getAnnotation(EnforceRoleInExercise.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(EnforceRoleInExercise.class);
        }
        if (annotation == null) {
            for (Annotation a : method.getDeclaredAnnotations()) {
                annotation = a.annotationType().getAnnotation(EnforceRoleInExercise.class);
                if (annotation != null) {
                    break;
                }
            }
        }
        if (annotation == null) {
            for (Annotation a : method.getDeclaringClass().getDeclaredAnnotations()) {
                annotation = a.annotationType().getAnnotation(EnforceRoleInExercise.class);
                if (annotation != null) {
                    break;
                }
            }
        }
        return annotation;
    }

    /**
     * Extracts the exerciseId from the method arguments
     *
     * @param joinPoint the join point
     * @return the exerciseId if it is present, empty otherwise
     */
    private Optional<Long> getExerciseId(ProceedingJoinPoint joinPoint, EnforceRoleInExercise enforceRoleInExercise) {
        final String exerciseIdFieldName = enforceRoleInExercise.exerciseIdFieldName();
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final int indexOfExerciseId = Arrays.asList(signature.getParameterNames()).indexOf(exerciseIdFieldName);
        Object[] args = joinPoint.getArgs();

        if (indexOfExerciseId < 0 || args.length <= indexOfExerciseId) {
            return Optional.empty();
        }

        if (args[indexOfExerciseId] instanceof Long) {
            return Optional.of((Long) args[indexOfExerciseId]);
        }
        return Optional.empty();
    }
}
