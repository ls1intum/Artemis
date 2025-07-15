package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise;

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
public class EnforceRoleInExerciseAspect extends EnforceRoleInResourceAspect {

    public EnforceRoleInExerciseAspect(AuthorizationCheckService authorizationCheckService) {
        super(authorizationCheckService, EnforceRoleInExercise.class, EnforceAtLeastStudentInExercise.class, EnforceAtLeastTutorInExercise.class,
                EnforceAtLeastEditorInExercise.class, EnforceAtLeastInstructorInExercise.class);
    }

    /**
     * Pointcut around all methods or classes annotated with {@link EnforceRoleInExercise}.
     */
    @Pointcut("@within(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceRoleInExercise) || @annotation(de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceRoleInExercise) || execution(@(@de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceRoleInExercise *) * *(..))")
    @Override
    protected void callAt() {
    }

    @Override
    protected void authorizationCheck(Role role, long resourceId) {
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(role, resourceId);
    }
}
