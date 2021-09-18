package de.tum.in.www1.artemis.service;

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
            NotificationType[] tmpNotificationTypes = this.findCorrespondingNotificationTypesForNotificationOption(option);
            for (NotificationType type : tmpNotificationTypes) {
                resultingMap.put(type, option.isWebapp());
            }
        }
        return resultingMap;
    }

    /**
     * This is the place where the mapping between NotificationOptions and NotificationType happens on the server side
     * Each NotificationOption can be based on multiple different NotificationTypes
     * @param notificationOption which corresponding NotificationTypes should be found
     * @return the corresponding NotificationType(s)
     */
    private NotificationType[] findCorrespondingNotificationTypesForNotificationOption(NotificationOption notificationOption) {
        switch (notificationOption.getOptionSpecifier()) {
            case "notification.exercise-notification.exercise-created-or-started": {
                return new NotificationType[] { NotificationType.EXERCISE_CREATED };
            }
            case "notification.exercise-notification.exercise-open-for-practice": {
                return new NotificationType[] { NotificationType.EXERCISE_PRACTICE };
            }
            case "notification.exercise-notification.new-post-exercises": {
                return new NotificationType[] { NotificationType.NEW_POST_FOR_EXERCISE };
            }
            case "notification.exercise-notification.new-answer-post-exercises": {
                return new NotificationType[] { NotificationType.NEW_ANSWER_POST_FOR_EXERCISE };
            }
            case "notification.lecture-notification.attachment-changes": {
                return new NotificationType[] { NotificationType.ATTACHMENT_CHANGE };
            }
            case "notification.lecture-notification.new-post-for-lecture": {
                return new NotificationType[] { NotificationType.NEW_POST_FOR_LECTURE };
            }
            case "notification.lecture-notification.new-answer-post-for-lecture": {
                return new NotificationType[] { NotificationType.NEW_ANSWER_POST_FOR_LECTURE };
            }
            case "notification.instructor-exclusive-notification.course-and-exam-archiving-started": {
                return new NotificationType[] { NotificationType.EXAM_ARCHIVE_STARTED, NotificationType.COURSE_ARCHIVE_STARTED };
            }
            default: {
                return new NotificationType[0];
            }
        }
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
