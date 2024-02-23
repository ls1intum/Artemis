package de.tum.in.www1.artemis.security.annotations;

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
public class RoleInCourseAspect {

    private final AuthorizationCheckService authorizationCheckService;

    public RoleInCourseAspect(AuthorizationCheckService authorizationCheckService) {
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Pointcut around all methods or classes annotated with {@link RoleInCourse}.
     *
     * @param roleInCourse The annotation containing the relevant role
     */
    @Pointcut("@within(roleInCourse) || @annotation(roleInCourse)")
    public void callAt(RoleInCourse roleInCourse) {
    }

    /**
     * Aspect around all methods for which a role in course has been activated. Will check if the user has the required role in the course and only execute the underlying method if
     * the user has the required role. Will otherwise return forbidden (as response entity)
     *
     * @param joinPoint    Proceeding join point of the aspect
     * @param roleInCourse The annotation containing the required role
     * @return The original return value of the called method, if all features are enabled, a forbidden response entity otherwise
     * @throws Throwable If there was any error during method execution (both the aspect or the actual called method)
     */
    @Around(value = "callAt(roleInCourse)", argNames = "joinPoint,roleInCourse")
    public Object around(ProceedingJoinPoint joinPoint, RoleInCourse roleInCourse) throws Throwable {
        final var courseId = getCourseId(joinPoint).orElseThrow(() -> new IllegalArgumentException("Method annotated with @RoleInCourse must have a parameter named 'courseId'"));
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(roleInCourse.value(), courseId);
        return joinPoint.proceed();
    }

    /**
     * Extracts the courseId from the method arguments
     *
     * @param joinPoint the join point
     * @return the courseId if it is present, empty otherwise
     */
    private Optional<Long> getCourseId(ProceedingJoinPoint joinPoint) {
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final int indexOfCourseId = Arrays.asList(signature.getParameterNames()).indexOf("courseId");
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
