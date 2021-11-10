package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.findCorrespondingNotificationType;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;

@Service
public class NotificationSettingsService {

    private NotificationSettingRepository notificationSettingRepository;

    // notification settings settingIds analogous to client side
    // course wide discussion notification setting group
    private final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = "notification.course-wide-discussion.new-course-post";

    private final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = "notification.course-wide-discussion.new-reply-for-course-post";

    private final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST = "notification.course-wide-discussion.new-announcement-post";

    // exercise notification setting group
    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED = "notification.exercise-notification.exercise-released";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = "notification.exercise-notification.exercise-open-for-practice";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST = "notification.exercise-notification.new-exercise-post";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST = "notification.exercise-notification.new-reply-for-exercise-post";

    // lecture notification settings group
    private final static String NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = "notification.lecture-notification.attachment-changes";

    private final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST = "notification.lecture-notification.new-lecture-post";

    private final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST = "notification.lecture-notification.new-reply-for-lecture-post";

    // instructor exclusive notification setting group
    private final static String NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED = "notification.instructor-exclusive-notification.course-and-exam-archiving-started";

    // if webapp or email is not explicitly set for a specific setting -> no support for this communication channel for this setting
    // this has to match the properties in the notification settings structure file on the client that hides the related UI elements
    public final static Set<NotificationSetting> DEFAULT_NOTIFICATION_SETTINGS = new HashSet<>(Arrays.asList(
            // course wide discussion notification setting group
            new NotificationSetting(true, false, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST),
            new NotificationSetting(true, false, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST),
            new NotificationSetting(true, true, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST),
            // exercise notification setting group
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST),
            // lecture notification settings group
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST),
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
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST, new NotificationType[] { NEW_REPLY_FOR_COURSE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST, new NotificationType[] { NEW_ANNOUNCEMENT_POST }), Map.entry(
                    NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED, new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED }));

    // This set has to equal the UI configuration in the client notification settings structure file!
    private static final Set<NotificationType> NOTIFICATION_TYPES_WITH_EMAIL_SUPPORT = Set.of(EXERCISE_RELEASED, EXERCISE_PRACTICE, ATTACHMENT_CHANGE, NEW_ANNOUNCEMENT_POST);

    public NotificationSettingsService(NotificationSettingRepository notificationSettingRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
    }

    /**
     * Checks if a notification (i.e. its type based on title) is allowed by the respective notification settings of the provided user
     * @param notification which type (based on title) should be checked
     * @param user whose notification settings will be used for checking
     * @return true if the type is allowed else false
     */
    public boolean checkIfNotificationEmailIsAllowedBySettingsForGivenUser(Notification notification, User user) {
        NotificationType type = findCorrespondingNotificationType(notification.getTitle());

        Set<NotificationSetting> notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(user.getId());

        Set<NotificationType> deactivatedTypes;

        // the urgent emails were already sent
        // if the user has not yet changes his settings they will be of size 0 -> use default
        if (notificationSettings.isEmpty()) {
            deactivatedTypes = findDeactivatedNotificationTypes(false, DEFAULT_NOTIFICATION_SETTINGS);
        }
        else {
            deactivatedTypes = findDeactivatedNotificationTypes(false, notificationSettings);
        }

        if (deactivatedTypes.isEmpty()) {
            return true;
        }
        return !deactivatedTypes.contains(type);
    }

    /**
     * Checks if the notification type has email support (per default not for an individual user!)
     * For some types there is no need for email support so they will be filtered out here.
     *
     * @param type of the notification
     * @return true if the type has email support else false
     */
    public boolean checkNotificationTypeForEmailSupport(NotificationType type) {
        return NOTIFICATION_TYPES_WITH_EMAIL_SUPPORT.contains(type);
    }

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationSettings
     * @param checkForWebapp indicates if the status for the webapp (true) or for email (false) should be used/checked
     * @param notificationSettings which should be mapped to their respective NotificationTypes and filtered by activation status
     * @return a set of NotificationTypes which are deactivated by the current user's notification settings
     */
    public Set<NotificationType> findDeactivatedNotificationTypes(boolean checkForWebapp, Set<NotificationSetting> notificationSettings) {
        Map<NotificationType, Boolean> notificationSettingWithActivationStatusMap = convertNotificationSettingsToNotificationTypesWithActivationStatus(checkForWebapp,
                notificationSettings);
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
     * @param checkForWebapp indicates if the map should look for the email or webapp activity
     * @return a map with key of NotificationType and value Boolean indicating which types are (de)activated by the user's notification settings
     */
    private Map<NotificationType, Boolean> convertNotificationSettingsToNotificationTypesWithActivationStatus(boolean checkForWebapp,
            Set<NotificationSetting> notificationSettings) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<>();
        for (NotificationSetting setting : notificationSettings) {
            NotificationType[] tmpNotificationTypes = NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP.getOrDefault(setting.getSettingId(), new NotificationType[0]);
            for (NotificationType type : tmpNotificationTypes) {
                resultingMap.put(type, checkForWebapp ? setting.isWebapp() : setting.isEmail());
            }
        }
        return resultingMap;
    }

    /**
     * Checks the personal notificationSettings retrieved from the DB.
     * If the loaded set is empty substitute it with the default settings
     * If the loaded set has a different size from the default settings both sets have to be merged
     * @param loadedNotificationSettingSet are the notification settings retrieved from the DB for the current user
     * @return the updated and correct notification settings
     */
    public Set<NotificationSetting> checkLoadedNotificationSettingsForCorrectness(Set<NotificationSetting> loadedNotificationSettingSet) {
        if (loadedNotificationSettingSet.isEmpty()) {
            return DEFAULT_NOTIFICATION_SETTINGS;
        }
        // default settings might have changed (e.g. number of settings) -> need to merge the saved settings with default ones (else errors appear)
        if (loadedNotificationSettingSet.size() != DEFAULT_NOTIFICATION_SETTINGS.size()) {
            Set<NotificationSetting> updatedNotificationSettingSet = new HashSet<>();
            updatedNotificationSettingSet.addAll(DEFAULT_NOTIFICATION_SETTINGS);

            loadedNotificationSettingSet.forEach(loadedSetting -> {
                DEFAULT_NOTIFICATION_SETTINGS.forEach(defaultSetting -> {
                    if (defaultSetting.getSettingId().equals(loadedSetting.getSettingId())) {
                        updatedNotificationSettingSet.remove(defaultSetting);
                        updatedNotificationSettingSet.add(loadedSetting);
                    }
                });
            });
            // update DB to fix inconsistencies and avoid redundant future merges
            notificationSettingRepository.saveAll(updatedNotificationSettingSet);
            return updatedNotificationSettingSet;
        }
        return loadedNotificationSettingSet;
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
