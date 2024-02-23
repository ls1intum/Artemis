package de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@PreAuthorize("hasRole('INSTRUCTOR')")
@EnforceRoleInCourse(Role.INSTRUCTOR)
public @interface EnforceAtLeastInstructorInCourse {
}
