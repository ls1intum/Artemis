package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit;

import org.aspectj.lang.annotation.Pointcut;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceRoleInResourceAspect;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

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
