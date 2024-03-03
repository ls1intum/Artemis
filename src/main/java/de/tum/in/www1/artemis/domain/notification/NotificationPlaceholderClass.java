package de.tum.in.www1.artemis.domain.notification;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface NotificationPlaceholderClass {

    NotificationType[] values();
}
