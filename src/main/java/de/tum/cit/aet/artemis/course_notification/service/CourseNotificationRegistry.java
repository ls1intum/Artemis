package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;

/**
 * Registry service that discovers and maps all {@link CourseNotification}
 * subtypes annotated with {@link CourseNotificationType} during application startup.
 * This registry serves as a central mapping between database tinyint values and their
 * corresponding notification class types, eliminating the need for manual registration
 * of new notification types.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationRegistry {

    private final Map<Short, Class<? extends CourseNotification>> notificationTypes = new HashMap<>();

    private final Map<Class<? extends CourseNotification>, Short> notificationTypeIdentifiers = new HashMap<>();

    /**
     * Constructs a new NotificationRegistry and automatically scans the application context
     * for all beans annotated with {@link CourseNotificationType}. The registry maps each
     * notification type's numeric identifier to its class type.
     *
     * @param context the Spring application context used to discover annotated beans
     */
    @Autowired
    public CourseNotificationRegistry(ApplicationContext context) {
        Map<String, Object> beans = context.getBeansWithAnnotation(CourseNotificationType.class);

        for (Object bean : beans.values()) {
            Class<?> beanClass = bean.getClass();
            // Handle proxy classes created by Spring just in case
            if (beanClass.getSimpleName().contains("$$")) {
                beanClass = beanClass.getSuperclass();
            }

            if (CourseNotification.class.isAssignableFrom(beanClass)) {
                CourseNotificationType annotation = beanClass.getAnnotation(CourseNotificationType.class);
                Short typeId = (short) annotation.value();

                @SuppressWarnings("unchecked")
                Class<? extends CourseNotification> notificationClass = (Class<? extends CourseNotification>) beanClass;
                notificationTypes.put(typeId, notificationClass);
                notificationTypeIdentifiers.put(notificationClass, typeId);
            }
        }
    }

    /**
     * Retrieves the notification class type that corresponds to the given database type identifier.
     *
     * @param typeId the database tinyint value representing a notification type
     * @return the corresponding notification class, or null if no mapping exists for the given typeId
     */
    public Class<? extends CourseNotification> getNotificationClass(Short typeId) {
        return notificationTypes.get(typeId);
    }

    /**
     * Retrieves the notification class type identifier that corresponds to the given class. If this returns null
     * for your given notification, this means you might have forgotten to add the {@link CourseNotificationType}
     * annotation to your notification.
     *
     * @param typeClass the class of a notification type
     * @return the corresponding database identifier, or null if no mapping exists for the given class
     */
    public Short getNotificationIdentifier(Class<? extends CourseNotification> typeClass) {
        return notificationTypeIdentifiers.get(typeClass);
    }
}
