package de.tum.in.www1.artemis.security.annotations.enforceroleincourse;

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
public class EnforceRoleInCourseAspect {

    private final AuthorizationCheckService authorizationCheckService;

    public EnforceRoleInCourseAspect(AuthorizationCheckService authorizationCheckService) {
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInCourse}.
     *
     * @param enforceRoleInCourse The annotation containing the relevant role
     */
    @Pointcut("@within(enforceRoleInCourse) || @annotation(enforceRoleInCourse)")
    public void callAt(EnforceRoleInCourse enforceRoleInCourse) {
    }

    /**
     * Aspect around all methods for which a role in course has been activated. Will check if the user has the required role in the course and only execute the underlying method if
     * the user has the required role. Will otherwise return forbidden (as response entity)
     *
     * @param joinPoint           Proceeding join point of the aspect
     * @param enforceRoleInCourse The annotation containing the required role
     * @return The original return value of the called method, if all features are enabled, a forbidden response entity otherwise
     * @throws Throwable If there was any error during method execution (both the aspect or the actual called method)
     */
    @Around(value = "callAt(enforceRoleInCourse)", argNames = "joinPoint,roleInCourse")
    public Object around(ProceedingJoinPoint joinPoint, EnforceRoleInCourse enforceRoleInCourse) throws Throwable {
        final var courseId = getCourseId(joinPoint, enforceRoleInCourse)
                .orElseThrow(() -> new IllegalArgumentException("Method annotated with @RoleInCourse must have a parameter named 'courseId'"));
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(enforceRoleInCourse.value(), courseId);
        return joinPoint.proceed();
    }

    /**
     * Extracts the courseId from the method arguments
     *
     * @param joinPoint the join point
     * @return the courseId if it is present, empty otherwise
     */
    private Optional<Long> getCourseId(ProceedingJoinPoint joinPoint, EnforceRoleInCourse enforceRoleInCourse) {
        final String courseIdFieldName = enforceRoleInCourse.courseIdFieldName();
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final int indexOfCourseId = Arrays.asList(signature.getParameterNames()).indexOf(courseIdFieldName);
        Object[] args = joinPoint.getArgs();

        if (indexOfCourseId < 0 || args.length <= indexOfCourseId) {
            return Optional.empty();
        }

        if (args[indexOfCourseId] instanceof Long) {
            return Optional.of((Long) args[indexOfCourseId]);
        }
        return Optional.empty();
    }
}
