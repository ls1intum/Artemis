package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

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

    private final Set<NotificationType> notificationTypesWithNoEmailSupport = Set.of(NotificationType.COURSE_ARCHIVE_STARTED, NotificationType.COURSE_ARCHIVE_FINISHED,
            NotificationType.EXAM_ARCHIVE_STARTED, NotificationType.EXAM_ARCHIVE_FINISHED);

    private final Set<NotificationType> urgentEmailNotificationTypes = Set.of(NotificationType.DUPLICATE_TEST_CASE, NotificationType.ILLEGAL_SUBMISSION);

    public NotificationSettingsService(NotificationSettingRepository notificationSettingRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
    }

    /**
     * Converts the provided NotificationType Set to a String Set (representing the titles from NotificationTitleTypeConstants)
     * @param types Set that should be converted to String
     * @return the converted String Set
     */
    public Set<String> convertNotificationTypesToTitles(Set<NotificationType> types) {
        return types.stream().map(type -> NotificationTitleTypeConstants.findCorrespondingNotificationTitle(type)).collect(Collectors.toSet());
    }

    /**
     * Checks if the notification type has email support
     * For some types there is no need for email support and they will be filtered out here.
     * @param type of the notification
     * @return true if the type has email support else false
     */
    public boolean checkNotificationTypeForEmailSupport(NotificationType type) {
        return !notificationTypesWithNoEmailSupport.contains(type);
    }

    /**
     * Checks if the notification type indicates an urgent email
     * i.e. an email should always be send (e.g. ILLEGAL_SUBMISSION) (users can not deactivate it via settings)
     * @param type of the notification
     * @return true if the type indicated an urgent case else false
     */
    public boolean checkNotificationTypeForEmailUrgency(NotificationType type) {
        return urgentEmailNotificationTypes.contains(type);
    }

    /**
     * Checks if a notification (i.e. its type based on title) is allowed by the respective notification settings of the provided user
     * @param notification which type (based on title) should be checked
     * @param user whose notification settings will be used for checking
     * @return true if the type is allowed else false
     */
    public boolean checkIfNotificationEmailIsAllowedBySettingsForGivenUser(Notification notification, User user) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());

        Set<NotificationSetting> notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(user.getId());

        Set<NotificationType> deactivatedTypes = findDeactivatedNotificationTypes(false, notificationSettings);

        if (deactivatedTypes.isEmpty()) {
            return true;
        }
        return !deactivatedTypes.contains(type);
    }

    // notification settings settingIds analogous to client side
    // exercise notification setting group
    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED = "notification.exercise-notification.exercise-created-or-started";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = "notification.exercise-notification.exercise-open-for-practice";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES = "notification.exercise-notification.new-post-exercises";

    private final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES = "notification.exercise-notification.new-answer-post-exercises";

    // lecture notification settings group
    private final static String NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = "notification.lecture-notification.attachment-changes";

    private final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE = "notification.lecture-notification.new-post-for-lecture";

    private final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE = "notification.lecture-notification.new-answer-post-for-lecture";

    // lecture notification setting group
    private final static String NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED = "notification.instructor-exclusive-notification.course-and-exam-archiving-started";

    public final static Set<NotificationSetting> DEFAULT_NOTIFICATION_SETTINGS = new HashSet<>(Arrays.asList(
            // exercise notification setting group
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES),
            // lecture notification settings group
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE),
            // lecture notification setting group
            new NotificationSetting(true, false, NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED)));

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationSettings
     * @param checkForWebapp indicates if the status for the webapp (true) or for email (false) should be used/checked
     * @param notificationSettings which should be mapped to their respective NotificationTypes and filtered by activation status
     * This is the place where the mapping between SettingId and NotificationTypes happens on the server side
     * Each SettingId can be based on multiple different NotificationTypes
     */
    private final static Map<String, NotificationType[]> NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP = Map.ofEntries(
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED, new NotificationType[] { EXERCISE_CREATED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, new NotificationType[] { EXERCISE_PRACTICE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES, new NotificationType[] { NEW_POST_FOR_EXERCISE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES, new NotificationType[] { NEW_ANSWER_POST_FOR_EXERCISE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, new NotificationType[] { ATTACHMENT_CHANGE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE, new NotificationType[] { NEW_POST_FOR_LECTURE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE, new NotificationType[] { NEW_ANSWER_POST_FOR_LECTURE }), Map.entry(
                    NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED, new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED }));

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
     * Converts the provided NotificationSetting to a map of corresponding NotificationTypes and activation status.
     * @param checkForWebapp indicates if the status for the webapp (true) or for email (false) should be used/checked
     * @param notificationSettings which will be mapped to their respective NotificationTypes with respect to their activation status
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
