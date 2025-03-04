package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AnnotationTypeFilter;
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

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationSettingPresetRegistry.class);

    private final Map<Integer, UserCourseNotificationSettingPreset> presets = new HashMap<>();

    /**
     * Constructs a new CourseNotificationSettingPresetRegistry and automatically scans the application context for all
     * classes annotated with {@link CourseNotificationSettingPreset} in the {@code course_notification.domain.setting_presets}
     * directory. The registry then creates an instance for all preset types.
     */
    public CourseNotificationSettingPresetRegistry() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CourseNotificationSettingPreset.class));
        String basePackage = "de.tum.cit.aet.artemis.course_notification.domain.setting_presets";

        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> classType = Class.forName(bd.getBeanClassName());

                if (UserCourseNotificationSettingPreset.class.isAssignableFrom(classType)) {
                    CourseNotificationSettingPreset annotation = classType.getAnnotation(CourseNotificationSettingPreset.class);
                    int typeId = annotation.value();

                    if (typeId == 0) {
                        throw new RuntimeException("The value 0 of the CourseNotificationSettingPreset decorator is "
                                + "reserved for 'Custom' specification and cannot be used. Please change to a different one.");
                    }

                    try {
                        UserCourseNotificationSettingPreset preset = (UserCourseNotificationSettingPreset) classType.getDeclaredConstructor().newInstance();
                        log.debug("Registering notification setting preset: {}, {}", typeId, classType.getSimpleName());
                        presets.put(typeId, preset);
                    }
                    catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        log.error("Failed to instantiate preset class {}: {}", classType.getName(), e.getMessage());
                    }
                }
            }
            catch (ClassNotFoundException e) {
                log.error("Failed to load notification setting preset class", e);
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
    protected boolean isPresetSettingEnabled(int typeId, Class<? extends CourseNotification> notificationType, NotificationSettingOption option) {
        if (!presets.containsKey(typeId)) {
            return false;
        }

        return presets.get(typeId).isEnabled(notificationType, option);
    }
}
