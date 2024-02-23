package de.tum.in.www1.artemis.security.annotations.enforceroleincourse;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@PreAuthorize("hasRole('EDITOR')")
@EnforceRoleInCourse(Role.EDITOR)
public @interface EnforceAtLeastEditorInCourse {
}
