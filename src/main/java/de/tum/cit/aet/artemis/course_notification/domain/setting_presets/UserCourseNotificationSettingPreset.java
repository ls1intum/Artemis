package de.tum.cit.aet.artemis.course_notification.domain.setting_presets;

import java.util.Map;

import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.NewPostNotification;

/**
 * Abstract base class for course notification setting presets.
 *
 * <p>
 * This class provides a framework for different notification preset configurations
 * that determine which notification types are enabled for various delivery options (web, email, push, etc.).
 * </p>
 *
 * <p>
 * Concrete subclasses should initialize the {@code presetMap} with specific notification settings
 * based on the desired preset behavior.
 * </p>
 */
public abstract class UserCourseNotificationSettingPreset {

    /**
     * A two-level map that stores notification settings.
     * <ul>
     * <li>The outer map uses notification types as keys (e.g., {@link NewPostNotification})</li>
     * <li>The inner map uses notification options as keys (e.g., {@link NotificationSettingOption}{@code .WEBAPP})</li>
     * <li>The boolean values indicate whether the notification is enabled (true) or disabled (false)</li>
     * </ul>
     */
    protected Map<Class<? extends CourseNotification>, Map<NotificationSettingOption, Boolean>> presetMap;

    /**
     * Determines whether a specific notification type and option combination is enabled in this preset.
     *
     * @param notificationType the class of the notification to check
     * @param option           the delivery option to check (e.g., WEBAPP, EMAIL, PUSH)
     * @return {@code true} if the notification type and option are enabled, {@code false} otherwise
     *         or if the notification type is not configured in this preset
     */
    public boolean isEnabled(Class<? extends CourseNotification> notificationType, NotificationSettingOption option) {
        if (!presetMap.containsKey(notificationType)) {
            return false;
        }

        return presetMap.get(notificationType).getOrDefault(option, false);
    }

    /**
     * Computes the name of the implementing preset in camelCase format.
     * This can be mapped to translations.
     *
     * @return Returns the simple name of the implementing class in camelCase format.
     */
    public String getReadablePresetIdentifier() {
        String className = this.getClass().getSimpleName();

        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }
}
