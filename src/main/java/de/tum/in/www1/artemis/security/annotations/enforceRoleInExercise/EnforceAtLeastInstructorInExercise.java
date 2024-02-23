package de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@PreAuthorize("hasRole('INSTRUCTOR')")
@EnforceRoleInExercise(Role.INSTRUCTOR)
public @interface EnforceAtLeastInstructorInExercise {
}
