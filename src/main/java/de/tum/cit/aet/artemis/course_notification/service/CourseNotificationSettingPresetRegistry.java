package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.domain.setting_presets.UserCourseNotificationSettingPreset;

/**
 * Registry service that provides options from the setting presets.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationSettingPresetRegistry {

    private final Map<Integer, UserCourseNotificationSettingPreset> presets = new HashMap<>();

    /**
     * Constructs a new CourseNotificationSettingPresetRegistry and automatically scans the application context
     * for all beans annotated with {@link CourseNotificationSettingPreset}. The registry then creates an instance
     * for all preset types.
     *
     * @param context the Spring application context used to discover annotated beans
     */
    @Autowired
    public CourseNotificationSettingPresetRegistry(ApplicationContext context) {
        Map<String, Object> beans = context.getBeansWithAnnotation(CourseNotificationSettingPreset.class);

        for (Object bean : beans.values()) {
            Class<?> beanClass = bean.getClass();
            // Handle proxy classes created by Spring just in case
            if (beanClass.getSimpleName().contains("$$")) {
                beanClass = beanClass.getSuperclass();
            }

            if (CourseNotification.class.isAssignableFrom(beanClass)) {
                CourseNotificationSettingPreset annotation = beanClass.getAnnotation(CourseNotificationSettingPreset.class);
                int typeId = annotation.value();

                if (typeId == 0) {
                    throw new RuntimeException("The value 0 of the CourseNotificationSettingPreset decorator is "
                            + "reserved for 'Custom' specification and cannot be used. Please change to a different one.");
                }

                try {
                    UserCourseNotificationSettingPreset preset = (UserCourseNotificationSettingPreset) beanClass.getDeclaredConstructor().newInstance();
                    presets.put(typeId, preset);
                }
                catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    continue;
                }
            }
        }
    }

    /**
     * Finds whether a setting is enabled or disabled for preset beans annotated with {@link CourseNotificationSettingPreset}.
     *
     * @param typeId           the identifier of the setting preset
     * @param notificationType the type of the notification to look for
     * @param option           the option to look for (e.g. Webapp, Email, Push, ...)
     *
     * @return Returns {@code true} if notification of this type is enabled by the preset and {@code false} otherwise.
     */
    public boolean isPresetSettingEnabled(int typeId, Class<? extends CourseNotification> notificationType, NotificationSettingOption option) {
        if (!presets.containsKey(typeId)) {
            return false;
        }

        return presets.get(typeId).isEnabled(notificationType, option);
    }
}
