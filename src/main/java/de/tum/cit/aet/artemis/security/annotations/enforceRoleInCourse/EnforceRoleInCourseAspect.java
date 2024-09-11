package de.tum.cit.aet.artemis.security.annotations.enforceRoleInCourse;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.annotations.EnforceRoleInResourceAspect;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
@Component
@Aspect
public class EnforceRoleInCourseAspect extends EnforceRoleInResourceAspect {

    public EnforceRoleInCourseAspect(AuthorizationCheckService authorizationCheckService) {
        super(authorizationCheckService, EnforceRoleInCourse.class, EnforceAtLeastStudentInCourse.class, EnforceAtLeastTutorInCourse.class, EnforceAtLeastEditorInCourse.class,
                EnforceAtLeastInstructorInCourse.class);
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInCourse}.
     */
    @Pointcut("@within(EnforceRoleInCourse) || @annotation(EnforceRoleInCourse) || execution(@(@EnforceRoleInCourse *) * *(..))")
    @Override
    protected void callAt() {
    }

    @Override
    protected void authorizationCheck(Role role, long resourceId) {
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(role, resourceId);
    }
}
