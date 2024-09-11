package de.tum.cit.aet.artemis.communication.domain.notification;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;

/**
 * Marks a method to be a notification placeholder creator. Allows automatic testing of placeholders in notifications.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotificationPlaceholderCreator {

    /**
     * The notification types of the annotated method. At least one is required.
     *
     * @return an array of {@link NotificationType} that the annotated method covers.
     */
    NotificationType[] values();
}
