package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit;

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
@EnforceRoleInLectureUnit(Role.TEACHING_ASSISTANT)
public @interface EnforceAtLeastTutorInLectureUnit {

    /**
     * The name of the field in the method parameters that contains the lecture unit id.
     * This is used to extract the lecture unit id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the lecture unit id
     */
    @AliasFor(annotation = EnforceRoleInLectureUnit.class)
    String resourceIdFieldName() default "lectureUnitId";
}
