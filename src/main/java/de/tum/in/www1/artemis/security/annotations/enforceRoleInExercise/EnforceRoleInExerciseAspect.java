package de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceRoleInResourceAspect;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

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
    @Pointcut("@within(EnforceRoleInExercise) || @annotation(EnforceRoleInExercise) || execution(@(@EnforceRoleInExercise *) * *(..))")
    @Override
    protected void callAt() {
    }

    @Override
    protected void authorizationCheck(Role role, long resourceId) {
        authorizationCheckService.checkIsAtLeastRoleInExerciseElseThrow(role, resourceId);
    }
}
