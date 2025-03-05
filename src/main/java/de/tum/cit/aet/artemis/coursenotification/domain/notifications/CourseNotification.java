package de.tum.cit.aet.artemis.coursenotification.domain.notifications;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.coursenotification.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.coursenotification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.coursenotification.domain.setting_presets.AllActivityUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.coursenotification.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.coursenotification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;

/**
 * Base class representing a notification type. If you want to create a new notification,
 * extend this and add the {@code @CourseNotificationType(n)} decorator to the class. The n in the decorator
 * represents the database identifier. Make sure to use a unique one. Things to keep in mind for new notifications:
 * <ul>
 * <li>For {@code WEBAPP}: Create the translations for the notification in the notification.json
 * {@code artemisApp.courseNotification.{camelCaseClassName}}.</li>
 * <li>For {@code EMAIL}: Create the e-mail template in the {@code src.resources.templates.mail} directory using {@code {camelCaseClassName}.html}
 * and create the localizations in the {@code src.resources.i18n.messages} directory.</li>
 * <li>For {@code PUSH}: Notify android and iOS developers about new notification and create translation strings accordingly</li>
 * </ul>
 *
 * <p>
 * Also make sure to add the notification types to the different setting presets, since otherwise all users will have
 * them disabled by default. See {@link DefaultUserCourseNotificationSettingPreset},
 * {@link AllActivityUserCourseNotificationSettingPreset}, {@link IgnoreUserCourseNotificationSettingPreset}
 * </p>
 */
public abstract class CourseNotification {

    public final long courseId;

    public final ZonedDateTime creationDate;

    private final Map<String, String> parameters;

    /**
     * Default constructor used when creating a new notification.
     */
    public CourseNotification(Long courseId, ZonedDateTime creationDate) {
        this.courseId = courseId;
        this.creationDate = creationDate;
        this.parameters = Map.of();
    }

    /**
     * Constructor used when loading an existing notification from the database. Will automatically initialize the
     * fields of the given notification.
     */
    public CourseNotification(Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        this.courseId = courseId;
        this.creationDate = creationDate;
        this.parameters = parameters;
        parseParameters();
    }

    /**
     * This method initializes the fields in a notification object. This is done to avoid huge walls of boilerplate
     * code when initializing fields as well as avoiding inconsistencies when manually naming keys. Make sure to
     * use non-null primitive types (+ String) only.
     */
    private void parseParameters() {
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getModifiers() == (java.lang.reflect.Modifier.PROTECTED)) {
                String value = parameters.get(field.getName());
                if (value != null) {
                    try {
                        if (field.getType().equals(Long.class) || field.getType().equals(long.class)) {
                            field.set(this, Long.parseLong(value));
                        }
                        else if (field.getType().equals(Integer.class) || field.getType().equals(int.class)) {
                            field.set(this, Integer.parseInt(value));
                        }
                        else if (field.getType().equals(Double.class) || field.getType().equals(double.class)) {
                            field.set(this, Double.parseDouble(value));
                        }
                        else if (field.getType().equals(Short.class) || field.getType().equals(short.class)) {
                            field.set(this, Short.parseShort(value));
                        }
                        else if (field.getType().equals(Float.class) || field.getType().equals(float.class)) {
                            field.set(this, Float.parseFloat(value));
                        }
                        else if (field.getType().equals(Boolean.class) || field.getType().equals(boolean.class)) {
                            field.set(this, Boolean.parseBoolean(value));
                        }
                        else if (field.getType().equals(Byte.class) || field.getType().equals(byte.class)) {
                            field.set(this, Byte.parseByte(value));
                        }
                        else if (field.getType().equals(Character.class) || field.getType().equals(char.class)) {
                            field.set(this, value.charAt(0));
                        }
                        else if (field.getType().equals(String.class)) {
                            field.set(this, value);
                        }
                        else {
                            throw new IllegalArgumentException("Unsupported type for notification: " + field.getType() + ". Make sure to use primitive types only.");
                        }
                    }
                    catch (IllegalAccessException | NumberFormatException e) {
                        throw new RuntimeException("Error assigning value to field: " + field.getName(), e);
                    }
                }
            }
        }
    }

    /**
     * This method initializes the parameters map using the fields of the notification object. This should result in a
     * map that can be stored in the database and later on used to re-hydrate the object in the parseParameters method.
     */
    private void initializeParameterMap() {
        if (!parameters.isEmpty()) {
            return;
        }

        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getModifiers() != (java.lang.reflect.Modifier.PROTECTED)) {
                continue;
            }

            try {
                Object value = field.get(this);
                if (value != null) {
                    parameters.put(field.getName(), value.toString());
                }
            }
            catch (IllegalAccessException e) {
                parameters.clear();
                throw new RuntimeException("Error getting value from field when initializing parameter map: " + field.getName(), e);
            }
        }
    }

    /**
     * Computes the name of the implementing notification in camelCase format.
     * For example, "NewPostNotification" would return "newPostNotification".
     * This is added to the notification payload and can be mapped to translations.
     *
     * @return Returns the simple name of the implementing class in camelCase format.
     */
    public String getReadableNotificationType() {
        String className = this.getClass().getSimpleName();

        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * Initializes parameter map and returns them. These can be stored in the database
     * as {@link CourseNotificationParameter}.
     *
     * @return Returns list of parameters as String-String key-value map.
     */
    public Map<String, String> getParameters() {
        initializeParameterMap();
        return parameters;
    }

    /**
     * This function tells the system which category the notification belongs to. E.g. "General" or "Communication".
     *
     * @return Returns the category of the notification
     */
    public abstract CourseNotificationCategory getCourseNotificationCategory();

    /**
     * This function should return the time interval a notification should stay in the database before getting
     * automatically deleted.
     *
     * @return Returns the time interval of how long a notification should be kept in the database
     */
    public abstract Duration getCleanupDuration();

    /**
     * This function should return which channels this notification supports (e.g. E-Mail, Push, etc.)
     *
     * @return Returns list of supported channels.
     */
    public abstract List<NotificationSettingOption> getSupportedChannels();
}
