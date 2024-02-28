package de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('TA')")
@EnforceRoleInCourse(Role.TEACHING_ASSISTANT)
public @interface EnforceAtLeastTutorInCourse {
}
