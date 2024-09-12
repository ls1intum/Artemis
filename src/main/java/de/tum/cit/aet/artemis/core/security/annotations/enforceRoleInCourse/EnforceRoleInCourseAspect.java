package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceRoleInResourceAspect;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

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
    @Pointcut("@within(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceRoleInCourse) || @annotation(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceRoleInCourse) || execution(@(@de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceRoleInCourse *) * *(..))")
    @Override
    protected void callAt() {
    }

    @Override
    protected void authorizationCheck(Role role, long resourceId) {
        authorizationCheckService.checkIsAtLeastRoleInCourseElseThrow(role, resourceId);
    }
}
