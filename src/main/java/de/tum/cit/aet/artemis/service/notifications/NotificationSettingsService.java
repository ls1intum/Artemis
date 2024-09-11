package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.findCorrespondingNotificationType;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.ATTACHMENT_CHANGE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_ADD_USER_CHANNEL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_ADD_USER_GROUP_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_CREATE_GROUP_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_CREATE_ONE_TO_ONE_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_NEW_MESSAGE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_NEW_REPLY_MESSAGE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_REMOVE_USER_CHANNEL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_REMOVE_USER_GROUP_CHAT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_USER_MENTIONED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.COURSE_ARCHIVE_STARTED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DATA_EXPORT_CREATED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DATA_EXPORT_FAILED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DUPLICATE_TEST_CASE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.EXAM_ARCHIVE_STARTED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.EXERCISE_PRACTICE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.EXERCISE_RELEASED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.FILE_SUBMISSION_SUCCESSFUL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_ANNOUNCEMENT_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_COURSE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_CPC_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_EXAM_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_EXERCISE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_LECTURE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_COURSE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXERCISE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_LECTURE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.PLAGIARISM_CASE_VERDICT_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.PROGRAMMING_TEST_CASES_CHANGED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.QUIZ_EXERCISE_STARTED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_ASSIGNED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_REGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UNASSIGNED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UPDATED;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants;
import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.NotificationSetting;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;

@Profile(PROFILE_CORE)
@Service
public class NotificationSettingsService {

    private final NotificationSettingRepository notificationSettingRepository;

    // notification settings settingIds analogous to client side

    // weekly summary
    public static final String NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY = "notification.weekly-summary.basic-weekly-summary";

    // course wide discussion notification setting group
    public static final String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = "notification.course-wide-discussion.new-course-post";

    public static final String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = "notification.course-wide-discussion.new-reply-for-course-post";

    public static final String NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST = "notification.course-wide-discussion.new-announcement-post";

    // exercise notification setting group
    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED = "notification.exercise-notification.exercise-submission-assessed";

    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED = "notification.exercise-notification.exercise-released";

    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = "notification.exercise-notification.exercise-open-for-practice";

    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST = "notification.exercise-notification.new-exercise-post";

    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST = "notification.exercise-notification.new-reply-for-exercise-post";

    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL = "notification.exercise-notification.file-submission-successful";

    public static final String NOTIFICATION__EXERCISE_NOTIFICATION__QUIZ_START_REMINDER = "notification.exercise-notification.quiz_start_reminder";

    // lecture notification settings group
    public static final String NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = "notification.lecture-notification.attachment-changes";

    public static final String NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST = "notification.lecture-notification.new-lecture-post";

    public static final String NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST = "notification.lecture-notification.new-reply-for-lecture-post";

    // exam notification setting group
    public static final String NOTIFICATION__EXAM_NOTIFICATION__NEW_EXAM_POST = "notification.exam-notification.new-exam-post";

    public static final String NOTIFICATION__EXAM_NOTIFICATION__NEW_REPLY_FOR_EXAM_POST = "notification.exam-notification.new-reply-for-exam-post";

    // tutorial group notification settings group
    public static final String NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION = "notification.tutorial-group-notification.tutorial-group-registration";

    public static final String NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE = "notification.tutorial-group-notification.tutorial-group-delete-update";

    // tutor notification setting group
    public static final String NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION = "notification.tutor-notification.tutorial-group-registration";

    public static final String NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_ASSIGN_UNASSIGN = "notification.tutor-notification.tutorial-group-assign-unassign";

    // editor notification setting group
    public static final String NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED = "notification.editor-notification.programming-test-cases-changed";

    // instructor notification setting group
    public static final String NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED = "notification.instructor-notification.course-and-exam-archiving-started";

    // user notification setting group
    public static final String NOTIFICATION__USER_NOTIFICATION__CONVERSATION_NEW_MESSAGE = "notification.user-notification.conversation-message";

    public static final String NOTIFICATION__USER_NOTIFICATION__NEW_REPLY_IN_CONVERSATION_MESSAGE = "notification.user-notification.new-reply-in-conversation";

    public static final String NOTIFICATION__USER_NOTIFICATION__USER_MENTION = "notification.user-notification.user-mention";

    public static final String NOTIFICATION_USER_NOTIFICATION_DATA_EXPORT_CREATED = "notification.user-notification.data-export-created";

    public static final String NOTIFICATION_USER_NOTIFICATION_DATA_EXPORT_FAILED = "notification.user-notification.data-export-failed";

    // if webapp or email is not explicitly set for a specific setting -> no support for this communication channel for this setting
    // this has to match the properties in the notification settings structure file on the client that hides the related UI elements
    public static final Set<NotificationSetting> DEFAULT_NOTIFICATION_SETTINGS = new HashSet<>(Arrays.asList(
            // weekly summary
            new NotificationSetting(false, false, false, NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY),
            // course wide discussion notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST),
            new NotificationSetting(true, false, true, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST),
            new NotificationSetting(true, true, true, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST),
            // exercise notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED),
            new NotificationSetting(true, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED),
            new NotificationSetting(true, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE),
            new NotificationSetting(false, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL),
            new NotificationSetting(true, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST),
            new NotificationSetting(true, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST),
            new NotificationSetting(false, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__QUIZ_START_REMINDER),
            // lecture notification settings group
            new NotificationSetting(true, false, true, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES),
            new NotificationSetting(true, false, true, NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST),
            new NotificationSetting(true, false, true, NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST),
            // exam notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__EXAM_NOTIFICATION__NEW_EXAM_POST),
            new NotificationSetting(true, false, true, NOTIFICATION__EXAM_NOTIFICATION__NEW_REPLY_FOR_EXAM_POST),
            // tutorial group notification settings group
            new NotificationSetting(true, false, true, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION),
            new NotificationSetting(true, false, true, NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE),
            // tutor notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION),
            // editor notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED),
            // instructor notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED),
            new NotificationSetting(true, false, true, NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_ASSIGN_UNASSIGN),
            // user new message notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__USER_NOTIFICATION__CONVERSATION_NEW_MESSAGE),
            new NotificationSetting(true, false, true, NOTIFICATION__USER_NOTIFICATION__NEW_REPLY_IN_CONVERSATION_MESSAGE),
            // user mention notification setting group
            new NotificationSetting(true, false, true, NOTIFICATION__USER_NOTIFICATION__USER_MENTION),
            // data export notification setting (cannot be overridden by user)
            new NotificationSetting(true, true, true, NOTIFICATION_USER_NOTIFICATION_DATA_EXPORT_FAILED),
            new NotificationSetting(true, true, true, NOTIFICATION_USER_NOTIFICATION_DATA_EXPORT_CREATED)));

    /**
     * This is the place where the mapping between SettingId and NotificationTypes happens on the server side
     * Each SettingId can be based on multiple different NotificationTypes
     */
    private static final Map<String, NotificationType[]> NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TYPES_MAP = Map.ofEntries(
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED, new NotificationType[] { EXERCISE_SUBMISSION_ASSESSED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED, new NotificationType[] { EXERCISE_RELEASED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, new NotificationType[] { EXERCISE_PRACTICE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST, new NotificationType[] { NEW_EXERCISE_POST }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST, new NotificationType[] { NEW_REPLY_FOR_EXERCISE_POST }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL, new NotificationType[] { FILE_SUBMISSION_SUCCESSFUL }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__QUIZ_START_REMINDER, new NotificationType[] { QUIZ_EXERCISE_STARTED }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, new NotificationType[] { ATTACHMENT_CHANGE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST, new NotificationType[] { NEW_LECTURE_POST }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST, new NotificationType[] { NEW_REPLY_FOR_LECTURE_POST }),
            Map.entry(NOTIFICATION__EXAM_NOTIFICATION__NEW_EXAM_POST, new NotificationType[] { NEW_EXAM_POST }),
            Map.entry(NOTIFICATION__EXAM_NOTIFICATION__NEW_REPLY_FOR_EXAM_POST, new NotificationType[] { NEW_EXAM_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST, new NotificationType[] { NEW_COURSE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST, new NotificationType[] { NEW_REPLY_FOR_COURSE_POST }),
            Map.entry(NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST, new NotificationType[] { NEW_ANNOUNCEMENT_POST }),
            Map.entry(NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED, new NotificationType[] { PROGRAMMING_TEST_CASES_CHANGED }),
            Map.entry(NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED, new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED }),
            Map.entry(NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE, new NotificationType[] { TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_UPDATED }),
            Map.entry(NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
                    new NotificationType[] { TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR }),
            Map.entry(NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
                    new NotificationType[] { TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT }),
            Map.entry(NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_ASSIGN_UNASSIGN, new NotificationType[] { TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED }),
            Map.entry(NOTIFICATION__USER_NOTIFICATION__CONVERSATION_NEW_MESSAGE,
                    new NotificationType[] { CONVERSATION_NEW_MESSAGE, CONVERSATION_CREATE_ONE_TO_ONE_CHAT, CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT,
                            CONVERSATION_ADD_USER_CHANNEL, CONVERSATION_REMOVE_USER_GROUP_CHAT, CONVERSATION_REMOVE_USER_CHANNEL }),
            Map.entry(NOTIFICATION__USER_NOTIFICATION__NEW_REPLY_IN_CONVERSATION_MESSAGE, new NotificationType[] { CONVERSATION_NEW_REPLY_MESSAGE }),
            Map.entry(NOTIFICATION__USER_NOTIFICATION__USER_MENTION, new NotificationType[] { CONVERSATION_USER_MENTIONED }));

    // This set has to equal the UI configuration in the client notification settings structure file!
    // More information on supported notification types can be found here: https://docs.artemis.cit.tum.de/user/notifications/
    // Please adapt the above docs if you change the supported notification types
    private static final Set<NotificationType> NOTIFICATION_TYPES_WITH_INSTANT_NOTIFICATION_SUPPORT = Set.of(EXERCISE_RELEASED, EXERCISE_PRACTICE, ATTACHMENT_CHANGE,
            NEW_ANNOUNCEMENT_POST, FILE_SUBMISSION_SUCCESSFUL, EXERCISE_SUBMISSION_ASSESSED, DUPLICATE_TEST_CASE, NEW_PLAGIARISM_CASE_STUDENT, NEW_CPC_PLAGIARISM_CASE_STUDENT,
            PLAGIARISM_CASE_VERDICT_STUDENT, TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR,
            TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_UPDATED, TUTORIAL_GROUP_ASSIGNED,
            TUTORIAL_GROUP_UNASSIGNED, NEW_EXERCISE_POST, NEW_LECTURE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_COURSE_POST, NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_EXERCISE_POST,
            QUIZ_EXERCISE_STARTED, DATA_EXPORT_CREATED, DATA_EXPORT_FAILED, CONVERSATION_NEW_MESSAGE, CONVERSATION_NEW_REPLY_MESSAGE);

    // More information on supported notification types can be found here: https://docs.artemis.cit.tum.de/user/notifications/
    // Please adapt the above docs if you change the supported notification types
    private static final Set<NotificationType> INSTANT_NOTIFICATION_TYPES_WITHOUT_EMAIL_SUPPORT = Set.of(QUIZ_EXERCISE_STARTED, NEW_EXERCISE_POST, NEW_LECTURE_POST,
            NEW_REPLY_FOR_LECTURE_POST, NEW_COURSE_POST, NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_EXERCISE_POST, CONVERSATION_NEW_MESSAGE, CONVERSATION_NEW_REPLY_MESSAGE);

    public NotificationSettingsService(NotificationSettingRepository notificationSettingRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
    }

    /**
     * Checks if a notification (i.e. its type based on title) is allowed by the respective notification settings of the provided user
     *
     * @param notification         which type (based on title) should be checked
     * @param user                 whose notification settings will be used for checking
     * @param communicationChannel which channel to use (e.g. email or webapp or push)
     * @return true if the type is allowed else false
     */
    public boolean checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(Notification notification, User user,
            NotificationSettingsCommunicationChannel communicationChannel) {
        Set<User> users = filterUsersByNotificationIsAllowedInCommunicationChannelBySettings(notification, Set.of(user), communicationChannel);
        return !users.isEmpty();
    }

    /**
     * Filters the given user array based on if the notification (i.e. its type based on title) is allowed by the respective notification settings
     *
     * @param notification         which type (based on title) should be checked
     * @param users                whose notification settings will be used for checking
     * @param communicationChannel which channel to use (e.g. email or webapp or push)
     * @return filtered user list
     */
    public Set<User> filterUsersByNotificationIsAllowedInCommunicationChannelBySettings(Notification notification, Set<User> users,
            NotificationSettingsCommunicationChannel communicationChannel) {
        NotificationType type = findCorrespondingNotificationType(notification.getTitle());

        Set<NotificationSetting> decidedNotificationSettings = notificationSettingRepository
                .findAllNotificationSettingsForRecipientsWithId(users.stream().map(DomainObject::getId).toList());
        Set<NotificationSetting> notificationSettings = new HashSet<>(decidedNotificationSettings);

        return users.stream().filter(user -> {
            // for those notification types that are not explicitly set by the user, we use the default settings
            Set<String> decidedIds = decidedNotificationSettings.stream().filter(notificationSetting -> notificationSetting.getUser().getId().equals(user.getId()))
                    .map(NotificationSetting::getSettingId).collect(Collectors.toSet());
            for (NotificationSetting defaultSetting : DEFAULT_NOTIFICATION_SETTINGS) {
                if (!decidedIds.contains(defaultSetting.getSettingId())) {
                    notificationSettings
                            .add(new NotificationSetting(user, defaultSetting.isWebapp(), defaultSetting.isEmail(), defaultSetting.isPush(), defaultSetting.getSettingId()));
                }
            }

            Set<NotificationType> deactivatedTypes = findDeactivatedNotificationTypes(communicationChannel, notificationSettings);
            return !deactivatedTypes.contains(type);
        }).collect(Collectors.toSet());
    }

    /**
     * Checks if the notification type has instant notification support (per default not for an individual user!)
     * For some types there is no need for instant notification support so they will be filtered out here.
     *
     * @param type of the notification
     * @return true if the type has instant notification support else false
     */
    public boolean checkNotificationTypeForInstantNotificationSupport(NotificationType type) {
        return NOTIFICATION_TYPES_WITH_INSTANT_NOTIFICATION_SUPPORT.contains(type);
    }

    /**
     * Checks if the notification type has email notification support (per default not for an individual user!)
     * For some types there is no need for email notification support so they will be filtered out here.
     *
     * @param type of the notification
     * @return true if the type has email support else false
     */
    public boolean checkNotificationTypeForEmailSupport(NotificationType type) {
        return !INSTANT_NOTIFICATION_TYPES_WITHOUT_EMAIL_SUPPORT.contains(type);
    }

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationSettings
     *
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
     *
     * @param types Set that should be converted to String
     * @return the converted String Set
     */
    public Set<String> convertNotificationTypesToTitles(Set<NotificationType> types) {
        return types.stream().map(NotificationConstants::findCorrespondingNotificationTitle).collect(Collectors.toSet());
    }

    /**
     * Converts the provided NotificationSetting to a map of corresponding NotificationTypes and activation status.
     *
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
                case PUSH -> Arrays.stream(tmpNotificationTypes).forEach(type -> resultingMap.put(type, setting.isPush()));
            }
        }
        return resultingMap;
    }

    /**
     * Extracts the settingsIds of a notification settings set
     * E.g. used to compare two sets of notification settings based on setting id
     *
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
     *
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
     *
     * @param userNotificationSettings are the notification settings retrieved from the DB for the current user
     * @param user                     the user for which the settings should be loaded
     * @return the updated and correct notification settings
     */
    public Set<NotificationSetting> checkLoadedNotificationSettingsForCorrectness(Set<NotificationSetting> userNotificationSettings, User user) {
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

            updatedDefaultNotificationSettings.forEach(userNotificationSetting -> userNotificationSetting.setUser(user));
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
     *
     * @param notificationSettings which might be saved the very first time and have no user set yet
     * @param currentUser          who should be set
     */
    public void setCurrentUser(NotificationSetting[] notificationSettings, User currentUser) {
        for (NotificationSetting notificationSetting : notificationSettings) {
            notificationSetting.setUser(currentUser);
        }
    }
}
