package de.tum.cit.aet.artemis.communication.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.communication.domain.setting_presets.UserCourseNotificationSettingPreset;

/**
 * Annotation that marks classes extending {@link UserCourseNotificationSettingPreset} with a unique numeric identifier.
 * This identifier is used to map between database representation (tinyint) and the corresponding
 * preset class type. Make sure to annotate new presets with this decorator.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CourseNotificationSettingPreset {

    /**
     * Returns the unique numeric identifier that represents this preset type in the database.
     *
     * @return the database tinyint value that maps to this preset type
     */
    int value();
}
