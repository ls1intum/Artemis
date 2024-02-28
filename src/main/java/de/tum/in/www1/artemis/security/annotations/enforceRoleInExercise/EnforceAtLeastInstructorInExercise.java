package de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('INSTRUCTOR')")
@EnforceRoleInExercise(Role.INSTRUCTOR)
public @interface EnforceAtLeastInstructorInExercise {
}
