package de.tum.cit.aet.artemis.communication.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;

/**
 * Annotation that marks classes extending {@link CourseNotification} with a unique numeric identifier.
 * This identifier is used to map between database representation (tinyint) and the corresponding
 * notification class type. Make sure to annotate new Notifications with this decorator.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CourseNotificationType {

    /**
     * Returns the unique numeric identifier that represents this notification type in the database.
     *
     * @return the database tinyint value that maps to this notification type
     */
    int value();
}
