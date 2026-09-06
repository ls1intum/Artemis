package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.AllActivityUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.IgnoreUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Base class representing a notification type. If you want to create a new notification,
 * extend this and add the {@code @CourseNotificationType(n)} decorator to the class. The n in the decorator
 * represents the database identifier. Make sure to use a unique one. Declare the values the notification renders with
 * as a record implementing {@link CourseNotificationPayloadDTO}, return it from {@link #payload()}, and rebuild it from
 * the stored rows in the constructor that reads a notification back. Its components are stored as course notification
 * parameters, one row each. Things to keep in mind for new notifications:
 * <ul>
 * <li>For {@code WEBAPP}: Create the translations for the notification in the notification.json
 * {@code artemisApp.courseNotification.{camelCaseClassName}}. All the components of your payload
 * will be injected into the translation string automatically. To control the icon that shows for the notification
 * as well as markdown rendering consult the {@code course-notification.service.ts}.</li>
 * <li>For {@code EMAIL}: Create the e-mail template in the {@code src.resources.templates.mail} directory using {@code {camelCaseClassName}.html}
 * and create the localizations in the {@code src.resources.i18n.messages} directory. All components of your payload
 * are made available automatically in the thymeleaf template under {@code parameters}.</li>
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

    public Long notificationId;

    public final long courseId;

    public final ZonedDateTime creationDate;

    protected String courseTitle;

    protected String courseIconUrl;

    private final Map<String, String> parameters;

    /**
     * Default constructor used when creating a new notification.
     */
    public CourseNotification(Long notificationId, Long courseId, String courseTitle, String courseIconUrl, ZonedDateTime creationDate) {
        this.notificationId = notificationId;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.courseIconUrl = courseIconUrl;
        this.creationDate = creationDate;
        this.parameters = new HashMap<>();
    }

    /**
     * Constructor used when loading an existing notification from the database. Will automatically initialize the
     * fields of the given notification.
     */
    public CourseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        this.notificationId = notificationId;
        this.courseId = courseId;
        this.creationDate = creationDate;
        this.parameters = parameters;
        parseSharedParameters();
    }

    /**
     * Reads the values every notification carries, whatever its type.
     * <p>
     * The type specific values are the payload's business: each notification builds its own record from these same
     * rows in its reading constructor.
     */
    private void parseSharedParameters() {
        courseTitle = parameters.get("courseTitle");
        courseIconUrl = parameters.get("courseIconUrl");
    }

    /**
     * The values this notification renders with, by name.
     * <p>
     * The payload's components, with the types they are declared with, plus the two values every notification carries.
     * A client reads them from the payload of its notification type; an email template reads them from the map the
     * mail service assembles out of this.
     *
     * @return the values, which the caller must not assume to be modifiable beyond its own copy
     */
    public Map<String, Object> getParameters() {
        Map<String, Object> values = CourseNotificationPayloads.asMap(payload());
        values.put("courseTitle", courseTitle);
        values.put("courseIconUrl", courseIconUrl);
        return values;
    }

    /**
     * The type specific values of this notification.
     *
     * @return the payload record of the implementing notification type
     */
    public abstract CourseNotificationPayloadDTO payload();

    /**
     * @return the title of the course the notification belongs to, which every notification renders
     */
    public String courseTitle() {
        return courseTitle;
    }

    /**
     * @return the icon of that course, absent when it has none
     */
    public String courseIconUrl() {
        return courseIconUrl;
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
    public abstract List<NotificationChannelOption> getSupportedChannels();

    /**
     * This function should return the relative webapp url (e.g /courses/:courseId/communication?conversationId=:conversationId).
     *
     * @return Returns the relative webapp URL as a string
     */
    public abstract String getRelativeWebAppUrl();
}
