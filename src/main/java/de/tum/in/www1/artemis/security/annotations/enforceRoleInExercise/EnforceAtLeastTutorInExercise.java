package de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('TA')")
@EnforceRoleInExercise(Role.TEACHING_ASSISTANT)
public @interface EnforceAtLeastTutorInExercise {
}
