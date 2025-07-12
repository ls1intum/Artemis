package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceRoleInResourceAspect;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
@Component
@Aspect
@Lazy
public class EnforceRoleInLectureAspect extends EnforceRoleInResourceAspect {

    public EnforceRoleInLectureAspect(AuthorizationCheckService authorizationCheckService) {
        super(authorizationCheckService, EnforceRoleInLecture.class, EnforceAtLeastStudentInLecture.class, EnforceAtLeastTutorInLecture.class, EnforceAtLeastEditorInLecture.class,
                EnforceAtLeastInstructorInLecture.class);
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInLecture}.
     */
    @Pointcut("@within(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceRoleInLecture) || @annotation(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceRoleInLecture) || execution(@(@de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceRoleInLecture *) * *(..))")
    @Override
    protected void callAt() {
    }

    @Override
    protected void authorizationCheck(Role role, long resourceId) {
        authorizationCheckService.checkIsAtLeastRoleInLectureElseThrow(role, resourceId);
    }
}
