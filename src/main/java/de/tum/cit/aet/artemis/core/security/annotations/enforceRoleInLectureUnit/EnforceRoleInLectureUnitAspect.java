package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit;

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
@Lazy
@Aspect
public class EnforceRoleInLectureUnitAspect extends EnforceRoleInResourceAspect {

    public EnforceRoleInLectureUnitAspect(AuthorizationCheckService authorizationCheckService) {
        super(authorizationCheckService, EnforceRoleInLectureUnit.class, EnforceAtLeastStudentInLectureUnit.class, EnforceAtLeastTutorInLectureUnit.class,
                EnforceAtLeastEditorInLectureUnit.class, EnforceAtLeastInstructorInLectureUnit.class);
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInLectureUnit}.
     */
    @Pointcut("@within(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceRoleInLectureUnit) || @annotation(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceRoleInLectureUnit) || execution(@(@de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceRoleInLectureUnit *) * *(..))")
    @Override
    protected void callAt() {
    }

    @Override
    protected void authorizationCheck(Role role, long resourceId) {
        authorizationCheckService.checkIsAtLeastRoleInLectureUnitElseThrow(role, resourceId);
    }
}
