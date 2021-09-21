package de.tum.in.www1.artemis.service;

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

    public final static Set<NotificationSetting> DEFAULT_NOTIFICATION_SETTINGS = new HashSet<>() {

        {
            // exercise notification setting group
            add(new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED));
            add(new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE));
            add(new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES));
            add(new NotificationSetting(true, false, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES));
            // lecture notification settings group
            add(new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES));
            add(new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE));
            add(new NotificationSetting(true, false, NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE));
            // lecture notification setting group
            add(new NotificationSetting(true, false, NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED));
        }
    };

    /**
     * This is the place where the mapping between OptionSpecifiers and NotificationTypes happens on the server side
     * Each OptionSpecifiers can be based on multiple different NotificationTypes
     */
    private final static Map<String, NotificationType[]> NOTIFICATION_OPTION_SPECIFIER_TO_NOTIFICATION_TYPES_MAP = Map.ofEntries(
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED, new NotificationType[] { EXERCISE_CREATED }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, new NotificationType[] { EXERCISE_PRACTICE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES, new NotificationType[] { NEW_POST_FOR_EXERCISE }),
            Map.entry(NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES, new NotificationType[] { NEW_ANSWER_POST_FOR_EXERCISE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, new NotificationType[] { ATTACHMENT_CHANGE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE, new NotificationType[] { NEW_POST_FOR_LECTURE }),
            Map.entry(NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE, new NotificationType[] { NEW_ANSWER_POST_FOR_LECTURE }), Map.entry(
                    NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED, new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED }));

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationOptions
     * @param notificationOptions which should be mapped to their respective NotificationTypes and filtered by activation status
     * @return a set of NotificationTypes which are deactivated by the current user's notification settings
     */
    public Set<NotificationType> findDeactivatedNotificationTypes(Set<NotificationSetting> notificationOptions) {
        Map<NotificationType, Boolean> notificationOptionWitchActivationStatusMap = convertNotificationOptionsToNotificationTypesWithActivationStatus(notificationOptions);
        Set<NotificationType> deactivatedNotificationTypes = new HashSet<>();
        notificationOptionWitchActivationStatusMap.forEach((notificationType, isActivated) -> {
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
     * Converts the provided NotificationOptions to a map of corresponding NotificationTypes and activation status.
     * @param notificationOptions which will be mapped to their respective NotificationTypes with respect to their activation status
     * @return a map with key of NotificationType and value Boolean indicating which types are (de)activated by the user's notification settings
     */
    private Map<NotificationType, Boolean> convertNotificationOptionsToNotificationTypesWithActivationStatus(Set<NotificationSetting> notificationOptions) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<>();
        for (NotificationSetting option : notificationOptions) {
            NotificationType[] tmpNotificationTypes = NOTIFICATION_OPTION_SPECIFIER_TO_NOTIFICATION_TYPES_MAP.getOrDefault(option.getSettingId(), new NotificationType[0]);
            for (NotificationType type : tmpNotificationTypes) {
                resultingMap.put(type, option.isWebapp());
            }
        }
        return resultingMap;
    }

    /**
     * Updates the notificationOptions by setting the current user
     * @param notificationOptions which might be saved the very first time and have no user set yet
     * @param currentUser who should be set
     */
    public void setCurrentUser(NotificationSetting[] notificationOptions, User currentUser) {
        for (NotificationSetting notificationOption : notificationOptions) {
            notificationOption.setUser(currentUser);
        }
    }
}
