package de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.security.annotations.AnnotationUtils.getAnnotation;

import java.util.Arrays;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
@Component
@Aspect
public class EnforceRoleInCourseAspect {

    private final AuthorizationCheckService authorizationCheckService;

    public EnforceRoleInCourseAspect(AuthorizationCheckService authorizationCheckService) {
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInCourse}.
     */
    @Pointcut("@within(EnforceRoleInCourse) || @annotation(EnforceRoleInCourse) || execution(@(@EnforceRoleInCourse *) * *(..))")
    public void callAt() {
    }

    /**
     * Aspect around all methods for which a role in course has been activated. Will check if the user has the required role in the course and only execute the underlying method if
     * the user has the required role. Will otherwise return forbidden (as response entity)
     *
     * @param joinPoint Proceeding join point of the aspect
     * @return The original return value of the called method, if all features are enabled, a forbidden response entity otherwise
     * @throws Throwable If there was any error during method execution (both the aspect or the actual called method)
     */
    @Around(value = "callAt()", argNames = "joinPoint")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        final var annotation = getAnnotation(EnforceRoleInCourse.class, joinPoint).orElseThrow();
        final var courseIdFieldName = getCourseIdFieldName(annotation, joinPoint);
        final var courseId = getCourseId(joinPoint, courseIdFieldName).orElseThrow(() -> new IllegalArgumentException(
                "Method annotated with @EnforceRoleInCourse must have a parameter named " + annotation.courseIdFieldName() + " of type long/Long."));
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(annotation.value(), courseId);
        return joinPoint.proceed();
    }

    /**
     * Extracts the courseIdFieldName from the annotation or the method arguments
     *
     * @param annotation the annotation
     * @param joinPoint  the join point
     * @return the courseIdFieldName
     */
    private String getCourseIdFieldName(EnforceRoleInCourse annotation, ProceedingJoinPoint joinPoint) {
        final var defaultFieldName = annotation.courseIdFieldName();
        Optional<String> fieldNameOfSimpleAnnotation = switch (annotation.value()) {
            case INSTRUCTOR -> getAnnotation(EnforceAtLeastInstructorInCourse.class, joinPoint).map(EnforceAtLeastInstructorInCourse::courseIdFieldName);
            case EDITOR -> getAnnotation(EnforceAtLeastEditorInCourse.class, joinPoint).map(EnforceAtLeastEditorInCourse::courseIdFieldName);
            case TEACHING_ASSISTANT -> getAnnotation(EnforceAtLeastTutorInCourse.class, joinPoint).map(EnforceAtLeastTutorInCourse::courseIdFieldName);
            case STUDENT -> getAnnotation(EnforceAtLeastStudentInCourse.class, joinPoint).map(EnforceAtLeastStudentInCourse::courseIdFieldName);
            default -> Optional.empty();
        };
        return fieldNameOfSimpleAnnotation.orElse(defaultFieldName);
    }

    /**
     * Extracts the courseId from the method arguments
     *
     * @param joinPoint         the join point
     * @param courseIdFieldName the courseIdFieldName
     * @return the courseId if it is present, empty otherwise
     */
    private Optional<Long> getCourseId(ProceedingJoinPoint joinPoint, String courseIdFieldName) {
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final int indexOfCourseId = Arrays.asList(signature.getParameterNames()).indexOf(courseIdFieldName);
        Object[] args = joinPoint.getArgs();

        if (indexOfCourseId < 0 || args.length <= indexOfCourseId) {
            return Optional.empty();
        }

        if (args[indexOfCourseId] instanceof Long courseId) {
            return Optional.of(courseId);
        }
        return Optional.empty();
    }
}
