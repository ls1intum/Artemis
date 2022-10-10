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

    private final NotificationSettingRepository notificationSettingRepository;

    // notification settings settingIds analogous to client side

    // weekly summary
    public final static String NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY = "notification.weekly-summary.basic-weekly-summary";

    // course wide discussion notification setting group
    public final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = "notification.course-wide-discussion.new-course-post";

    public final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = "notification.course-wide-discussion.new-reply-for-course-post";

    public final static String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST = "notification.course-wide-discussion.new-announcement-post";

    // exercise notification setting group
    public final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED = "notification.exercise-notification.exercise-submission-assessed";

    public final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED = "notification.exercise-notification.exercise-released";

    public final static String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = "notification.exercise-notification.exercise-open-for-practice";

    public final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST = "notification.exercise-notification.new-exercise-post";

    public final static String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST = "notification.exercise-notification.new-reply-for-exercise-post";

    public final static String NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL = "notification.exercise-notification.file-submission-successful";

    // lecture notification settings group
    public final static String NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = "notification.lecture-notification.attachment-changes";

    public final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST = "notification.lecture-notification.new-lecture-post";

    public final static String NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST = "notification.lecture-notification.new-reply-for-lecture-post";

    // tutorial group notification settings group
    public final static String NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION = "notification.tutorial-group-notification.tutorial-group-registration";

    public final static String NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE = "notification.tutorial-group-notification.tutorial-group-delete-update";

    // tutor notification setting group
    public final static String NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION = "notification.tutor-notification.tutorial-group-registration";

    // editor notification setting group
    public final static String NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED = "notification.editor-notification.programming-test-cases-changed";

    // instructor notification setting group
    public final static String NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED = "notification.instructor-notification.course-and-exam-archiving-started";

    // if webapp or email is not explicitly set for a specific setting -> no support for this communication channel for this setting
    // this has to match the properties in the notification settings structure file on the client that hides the related UI elements
    public final static Set<NotificationSetting> DEFAULT_NOTIFICATION_SETTINGS = new HashSet<>(Arrays.asList(
            // weekly summary
            new NotificationSetting(false, false, NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY),
            // course wide discussion notification setting group
            new NotificationSetting(true, false, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST),
            new NotificationSetting(true, false, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST),
            new NotificationSetting(true, true, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST),
            // exercise notification setting group
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE),
            new NotificationSetting(false, false, NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST),
            new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST),
            // lecture notification settings group
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST),
            new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST),
            // tutorial group notification settings group
            new NotificationSetting(true, false, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION),
            new NotificationSetting(true, false, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE),
            // tutor notification setting group
            new NotificationSetting(true, false, NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION),
            // editor notification setting group
            new NotificationSetting(true, false, NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED),
            // instructor notification setting group
            new NotificationSetting(true, false, NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED)));

    /**
     * This is the place where the mapping between SettingId and NotificationTypes happens on the server side
     * Each SettingId can be based on multiple different NotificationTypes
     */
    private final static Map<String, NotificationType[]> NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP = Map.ofEntries(
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED, new NotificationType[] { EXERCISE_SUBMISSION_ASSESSED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED, new NotificationType[] { EXERCISE_RELEASED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, new NotificationType[] { EXERCISE_PRACTICE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST, new NotificationType[] { NEW_EXERCISE_POST }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST, new NotificationType[] { NEW_REPLY_FOR_EXERCISE_POST }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL, new NotificationType[] { FILE_SUBMISSION_SUCCESSFUL }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, new NotificationType[] { ATTACHMENT_CHANGE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST, new NotificationType[] { NEW_LECTURE_POST }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST, new NotificationType[] { NEW_REPLY_FOR_LECTURE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST, new NotificationType[] { NEW_COURSE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST, new NotificationType[] { NEW_REPLY_FOR_COURSE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST, new NotificationType[] { NEW_ANNOUNCEMENT_POST }),
            Map.entry(NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED, new NotificationType[] { PROGRAMMING_TEST_CASES_CHANGED }),
            Map.entry(NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED, new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED }),
            Map.entry(NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE, new NotificationType[] { TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_UPDATED }),
            Map.entry(NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
                    new NotificationType[] { TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR }),
            Map.entry(NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
                    new NotificationType[] { TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT }));

    // This set has to equal the UI configuration in the client notification settings structure file!
    private static final Set<NotificationType> NOTIFICATION_TYPES_WITH_EMAIL_SUPPORT = Set.of(EXERCISE_RELEASED, EXERCISE_PRACTICE, ATTACHMENT_CHANGE, NEW_ANNOUNCEMENT_POST,
            FILE_SUBMISSION_SUCCESSFUL, EXERCISE_SUBMISSION_ASSESSED, DUPLICATE_TEST_CASE, NEW_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT,
            TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_TUTOR,
            TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_UPDATED);

    public NotificationSettingsService(NotificationSettingRepository notificationSettingRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
    }

    /**
     * Checks if a notification (i.e. its type based on title) is allowed by the respective notification settings of the provided user
     * @param notification which type (based on title) should be checked
     * @param user whose notification settings will be used for checking
     * @param communicationChannel which channel to use (e.g. email or webapp)
     * @return true if the type is allowed else false
     */
    public boolean checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(Notification notification, User user, NotificationSettingsCommunicationChannel communicationChannel) {
        NotificationType type = findCorrespondingNotificationType(notification.getTitle());

        Set<NotificationSetting> notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(user.getId());

        Set<NotificationType> deactivatedTypes;

        // the urgent emails were already sent at this point
        // if the user has not yet changed his settings they will be of size 0 -> use default
        if (notificationSettings.isEmpty()) {
            deactivatedTypes = findDeactivatedNotificationTypes(communicationChannel, DEFAULT_NOTIFICATION_SETTINGS);
        }
        else {
            deactivatedTypes = findDeactivatedNotificationTypes(communicationChannel, notificationSettings);
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
     * @param communicationChannel indicates if the status should be used/checked for the webapp or for email
     * @param notificationSettings which should be mapped to their respective NotificationTypes and filtered by activation status
     * @return a set of NotificationTypes which are deactivated by the current user's notification settings
     */
    public Set<NotificationType> findDeactivatedNotificationTypes(NotificationSettingsCommunicationChannel communicationChannel, Set<NotificationSetting> notificationSettings) {
        Map<NotificationType, Boolean> notificationSettingWithActivationStatusMap = convertNotificationSettingsToNotificationTypesWithActivationStatus(communicationChannel,
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
     * @param communicationChannel indicates if the map should look for the email or webapp activity
     * @return a map with key of NotificationType and value Boolean indicating which types are (de)activated by the user's notification settings
     */
    private Map<NotificationType, Boolean> convertNotificationSettingsToNotificationTypesWithActivationStatus(NotificationSettingsCommunicationChannel communicationChannel,
            Set<NotificationSetting> notificationSettings) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<>();
        for (NotificationSetting setting : notificationSettings) {
            NotificationType[] tmpNotificationTypes = NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP.getOrDefault(setting.getSettingId(), new NotificationType[0]);
            switch (communicationChannel) {
                case WEBAPP -> Arrays.stream(tmpNotificationTypes).forEach(type -> resultingMap.put(type, setting.isWebapp()));
                case EMAIL -> Arrays.stream(tmpNotificationTypes).forEach(type -> resultingMap.put(type, setting.isEmail()));
            }
        }
        return resultingMap;
    }

    /**
     * Extracts the settingsIds of a notification settings set
     * E.g. used to compare two sets of notification settings based on setting id
     * @param notificationSettings set which setting ids should be extracted
     * @return a set of settings ids
     */
    private Set<String> extractSettingsIdsFromNotificationSettingsSet(Set<NotificationSetting> notificationSettings) {
        Set<String> settingsIds = new HashSet<>();
        notificationSettings.forEach(setting -> settingsIds.add(setting.getSettingId()));
        return settingsIds;
    }

    /**
     * Compares two notification settings sets based on their notification setting ids
     * @param notificationSettingsA is the first set
     * @param notificationSettingsB is the second set
     * @return true if the notification setting ids of both are the same else return false
     */
    private boolean compareTwoNotificationSettingsSetsBasedOnSettingsId(Set<NotificationSetting> notificationSettingsA, Set<NotificationSetting> notificationSettingsB) {
        Set<String> settingIdsA = extractSettingsIdsFromNotificationSettingsSet(notificationSettingsA);
        Set<String> settingIdsB = extractSettingsIdsFromNotificationSettingsSet(notificationSettingsB);
        return settingIdsA.equals(settingIdsB);
    }

    /**
     * Checks the personal notificationSettings retrieved from the DB.
     * If the loaded set is empty substitute it with the default settings
     * If the loaded set has different notification setting ids than the default settings both sets have to be merged
     * @param userNotificationSettings are the notification settings retrieved from the DB for the current user
     * @return the updated and correct notification settings
     */
    public Set<NotificationSetting> checkLoadedNotificationSettingsForCorrectness(Set<NotificationSetting> userNotificationSettings) {
        if (userNotificationSettings.isEmpty()) {
            return DEFAULT_NOTIFICATION_SETTINGS;
        }
        // default settings might have changed (e.g. number of settings) -> need to merge the saved settings with default ones (else errors appear)

        if (!compareTwoNotificationSettingsSetsBasedOnSettingsId(userNotificationSettings, DEFAULT_NOTIFICATION_SETTINGS)) {
            Set<NotificationSetting> updatedDefaultNotificationSettings = new HashSet<>(DEFAULT_NOTIFICATION_SETTINGS);

            userNotificationSettings.forEach(userNotificationSetting -> DEFAULT_NOTIFICATION_SETTINGS.forEach(defaultSetting -> {
                if (defaultSetting.getSettingId().equals(userNotificationSetting.getSettingId())) {
                    updatedDefaultNotificationSettings.remove(defaultSetting);
                    updatedDefaultNotificationSettings.add(userNotificationSetting);
                }
            }));
            // update DB to fix inconsistencies and avoid redundant future merges
            // first remove all settings of the current user in the DB
            notificationSettingRepository.deleteAll(userNotificationSettings);
            // save correct merge to DB
            notificationSettingRepository.saveAll(updatedDefaultNotificationSettings);
            return updatedDefaultNotificationSettings;
        }
        return userNotificationSettings;
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
