package de.tum.in.www1.artemis.security.annotations.enforceroleincourse;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@PreAuthorize("hasRole('USER')")
@EnforceRoleInCourse(Role.STUDENT)
public @interface EnforceAtLeastStudentInCourse {
}
