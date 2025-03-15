package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;

/**
 * Registry service that discovers and maps all {@link CourseNotification}
 * subtypes annotated with {@link CourseNotificationType} during application startup.
 * This registry serves as a central mapping between database tinyint values and their
 * corresponding notification class types, eliminating the need for manual registration
 * of new notification types.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationRegistryService {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationRegistryService.class);

    private final Map<Short, Class<? extends CourseNotification>> notificationTypes = new HashMap<>();

    private final Map<Class<? extends CourseNotification>, Short> notificationTypeIdentifiers = new HashMap<>();

    /**
     * Constructs a new NotificationRegistry and automatically scans the application context for all classes annotated
     * with {@link CourseNotificationType} in the {@code communication.domain.notifications} directory. The
     * registry maps each notification type's numeric identifier to its class type.
     */
    public CourseNotificationRegistryService() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CourseNotificationType.class));
        String basePackage = "de.tum.cit.aet.artemis.communication.domain.course_notifications";

        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> classType = Class.forName(bd.getBeanClassName());

                if (CourseNotification.class.isAssignableFrom(classType)) {
                    CourseNotificationType annotation = classType.getAnnotation(CourseNotificationType.class);
                    Short typeId = (short) annotation.value();

                    @SuppressWarnings("unchecked")
                    Class<? extends CourseNotification> notificationClass = (Class<? extends CourseNotification>) classType;

                    log.debug("Registering notification: {}, {}", typeId, notificationClass);
                    notificationTypes.put(typeId, notificationClass);
                    notificationTypeIdentifiers.put(notificationClass, typeId);
                }
            }
            catch (ClassNotFoundException e) {
                log.error("Failed to load notification class", e);
            }
        }
    }

    /**
     * Retrieves the notification class type that corresponds to the given database type identifier.
     *
     * @param typeId the database tinyint value representing a notification type
     * @return the corresponding notification class, or null if no mapping exists for the given typeId
     */
    protected Class<? extends CourseNotification> getNotificationClass(Short typeId) {
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
    protected Short getNotificationIdentifier(Class<? extends CourseNotification> typeClass) {
        return notificationTypeIdentifiers.get(typeClass);
    }
}
