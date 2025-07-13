package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.cit.aet.artemis.core.security.Role;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('TA')")
@EnforceRoleInLecture(Role.TEACHING_ASSISTANT)
public @interface EnforceAtLeastTutorInLecture {

    /**
     * The name of the field in the method parameters that contains the lecture id.
     * This is used to extract the lecture id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the lecture id
     */
    @AliasFor(annotation = EnforceRoleInLecture.class)
    String resourceIdFieldName() default "lectureId";
}
