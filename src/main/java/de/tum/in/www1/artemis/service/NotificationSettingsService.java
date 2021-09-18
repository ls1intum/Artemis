package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;

@Service
public class NotificationSettingsService {

    /**
     * This is the place where the mapping between OptionSpecifiers and NotificationTypes happens on the server side
     * Each OptionSpecifiers can be based on multiple different NotificationTypes
     */
    private final static Map<String, NotificationType[]> NOTIFICATION_OPTION_SPECIFIER_TO_NOTIFICATION_TYPES_MAP = Map.of(
            "notification.exercise-notification.exercise-created-or-started", new NotificationType[] { EXERCISE_CREATED },
            "notification.exercise-notification.exercise-open-for-practice", new NotificationType[] { EXERCISE_PRACTICE }, "notification.exercise-notification.new-post-exercises",
            new NotificationType[] { NEW_POST_FOR_EXERCISE }, "notification.exercise-notification.new-answer-post-exercises",
            new NotificationType[] { NEW_ANSWER_POST_FOR_EXERCISE }, "notification.lecture-notification.attachment-changes", new NotificationType[] { ATTACHMENT_CHANGE },
            "notification.lecture-notification.new-post-for-lecture", new NotificationType[] { NEW_POST_FOR_LECTURE },
            "notification.lecture-notification.new-answer-post-for-lecture", new NotificationType[] { NEW_ANSWER_POST_FOR_LECTURE },
            "notification.instructor-exclusive-notification.course-and-exam-archiving-started", new NotificationType[] { EXAM_ARCHIVE_STARTED, COURSE_ARCHIVE_STARTED });

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationOptions
     * @param notificationOptions which should be mapped to their respective NotificationTypes and filtered by activation status
     * @return a set of NotificationTypes which are deactivated by the current user's notification settings
     */
    public Set<NotificationType> findDeactivatedNotificationTypes(Set<NotificationOption> notificationOptions) {
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
    private Map<NotificationType, Boolean> convertNotificationOptionsToNotificationTypesWithActivationStatus(Set<NotificationOption> notificationOptions) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<>();
        for (NotificationOption option : notificationOptions) {
            NotificationType[] tmpNotificationTypes = NOTIFICATION_OPTION_SPECIFIER_TO_NOTIFICATION_TYPES_MAP.getOrDefault(option.getOptionSpecifier(), new NotificationType[0]);
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
    public void setCurrentUser(NotificationOption[] notificationOptions, User currentUser) {
        for (NotificationOption notificationOption : notificationOptions) {
            notificationOption.setUser(currentUser);
        }
    }
}
