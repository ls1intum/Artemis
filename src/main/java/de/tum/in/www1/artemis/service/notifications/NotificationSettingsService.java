package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;

@Service
public class NotificationSettingsService {

    // notification settings settingIds analogous to client side
    // exercise notification setting group
    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED = "notification.exercise-notification.exercise-released";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = "notification.exercise-notification.exercise-open-for-practice";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST = "notification.exercise-notification.new-exercise-post";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST = "notification.exercise-notification.new-reply-for-exercise-post";

    // lecture notification settings group
    private final static String NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = "notification.lecture-notification.attachment-changes";

    private final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST = "notification.lecture-notification.new-lecture-post";

    private final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST = "notification.lecture-notification.new-reply-for-lecture-post";

    // course wide discussion notification setting group
    private final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = "notification.course-wide-discussion.new-course-post";

    private final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = "notification.course-wide-discussion.new-reply-for-course-post";

    // instructor exclusive notification setting group
    private final static String NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED = "notification.instructor-exclusive-notification.course-and-exam-archiving-started";

    public final static Set<NotificationSetting> DEFAULT_NOTIFICATION_SETTINGS = new HashSet<>(Arrays.asList(
            // exercise notification setting group
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST),
            // lecture notification settings group
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST),
            // course wide discussion notification setting group
            new NotificationSetting(true, false, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST),
            new NotificationSetting(true, false, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST),
            // instructor exclusive notification setting group
            new NotificationSetting(true, false, NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED)));

    /**
     * This is the place where the mapping between SettingId and NotificationTypes happens on the server side
     * Each SettingId can be based on multiple different NotificationTypes
     */
    private final static Map<String, NotificationType[]> NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP = Map.ofEntries(
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED, new NotificationType[] { EXERCISE_RELEASED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, new NotificationType[] { EXERCISE_PRACTICE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST, new NotificationType[] { NEW_EXERCISE_POST }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST, new NotificationType[] { NEW_REPLY_FOR_EXERCISE_POST }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, new NotificationType[] { ATTACHMENT_CHANGE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST, new NotificationType[] { NEW_LECTURE_POST }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST, new NotificationType[] { NEW_REPLY_FOR_LECTURE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST, new NotificationType[] { NEW_COURSE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST, new NotificationType[] { NEW_REPLY_FOR_COURSE_POST }), Map.entry(
                    NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED, new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED }));

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationSettings
     * @param notificationSettings which should be mapped to their respective NotificationTypes and filtered by activation status
     * @return a set of NotificationTypes which are deactivated by the current user's notification settings
     */
    public Set<NotificationType> findDeactivatedNotificationTypes(Set<NotificationSetting> notificationSettings) {
        Map<NotificationType, Boolean> notificationSettingWithActivationStatusMap = convertNotificationSettingsToNotificationTypesWithActivationStatus(notificationSettings);
        Set<NotificationType> deactivatedNotificationTypes = new HashSet<>();
        notificationSettingWithActivationStatusMap.forEach((notificationType, isActivated) -> {
            if (!isActivated) {
                deactivatedNotificationTypes.add(notificationType);
            }
        });
        return deactivatedNotificationTypes;
    }

    /**
     * Converts the provided NotificationType Set to a String Set (representing the titles from NotificationTitleTypeConstants)
     * @param types Set that should be converted to String
     * @return the converted String Set
     */
    public Set<String> convertNotificationTypesToTitles(Set<NotificationType> types) {
        return types.stream().map(NotificationTitleTypeConstants::findCorrespondingNotificationTitle).collect(Collectors.toSet());
    }

    /**
     * Converts the provided NotificationSetting to a map of corresponding NotificationTypes and activation status.
     * @param notificationSettings which will be mapped to their respective NotificationTypes with respect to their activation status
     * @return a map with key of NotificationType and value Boolean indicating which types are (de)activated by the user's notification settings
     */
    private Map<NotificationType, Boolean> convertNotificationSettingsToNotificationTypesWithActivationStatus(Set<NotificationSetting> notificationSettings) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<>();
        for (NotificationSetting setting : notificationSettings) {
            NotificationType[] tmpNotificationTypes = NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP.getOrDefault(setting.getSettingId(), new NotificationType[0]);
            for (NotificationType type : tmpNotificationTypes) {
                resultingMap.put(type, setting.isWebapp());
            }
        }
        return resultingMap;
    }

    /**
     * Updates the notificationSettings by setting the current user
     * @param notificationSettings which might be saved the very first time and have no user set yet
     * @param currentUser who should be set
     */
    public void setCurrentUser(NotificationSetting[] notificationSettings, User currentUser) {
        for (NotificationSetting notificationSetting : notificationSettings) {
            notificationSetting.setUser(currentUser);
        }
    }
}
