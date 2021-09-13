package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.repository.NotificationOptionRepository;

@Service
public class NotificationSettingsService {

    private NotificationOptionRepository notificationOptionRepository;

    private Set<NotificationType> notificationTypesWithNoEmailSupport = Set.of(NotificationType.COURSE_ARCHIVE_STARTED, NotificationType.COURSE_ARCHIVE_FINISHED,
            NotificationType.EXAM_ARCHIVE_STARTED, NotificationType.EXAM_ARCHIVE_FINISHED);

    private Set<NotificationType> urgendEmailNotificationTypes = Set.of(NotificationType.DUPLICATE_TEST_CASE, NotificationType.ILLEGAL_SUBMISSION);

    public NotificationSettingsService(NotificationOptionRepository notificationOptionRepository) {
        this.notificationOptionRepository = notificationOptionRepository;
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
        return urgendEmailNotificationTypes.contains(type);
    }

    /**
     * Checks if a notification (i.e. its type based on title) is allowed by the respective notification settings of the provided user
     * @param notification which type (based on title) should be checked
     * @param user whose notification settings will be used for checking
     * @return true if the type is allowed else false
     */
    public boolean checkIfNotificationEmailIsAllowedBySettingsForGivenUser(Notification notification, User user) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());

        Set<NotificationOption> notificationOptions = notificationOptionRepository.findAllNotificationOptionsForRecipientWithId(user.getId());

        Set<NotificationType> deactivatedTypes = findDeactivatedNotificationTypes(false, notificationOptions);

        if (deactivatedTypes.isEmpty()) {
            return true;
        }
        return !deactivatedTypes.contains(type);
    }

    /**
     * Finds the deactivated NotificationTypes based on the user's NotificationOptions
     * @param checkForWebapp indicates if the status for the webapp (true) or for email (false) should be used/checked
     * @param notificationOptions which should be mapped to their respective NotificationTypes and filtered by activation status
     * @return a set of NotificationTypes which are deactivated by the current user's notification settings
     */
    public Set<NotificationType> findDeactivatedNotificationTypes(boolean checkForWebapp, Set<NotificationOption> notificationOptions) {
        Map<NotificationType, Boolean> notificationOptionWitchActivationStatusMap = convertNotificationOptionsToNotificationTypesWithActivationStatus(checkForWebapp,
                notificationOptions);
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
        return types.stream().map(type -> NotificationTitleTypeConstants.findCorrespondingNotificationTitle(type)).collect(Collectors.toSet());
    }

    /**
     * Converts the provided NotificationOptions to a map of corresponding NotificationTypes and activation status.
     * @param checkForWebapp indicates if the status for the webapp (true) or for email (false) should be used/checked
     * @param notificationOptions which will be mapped to their respective NotificationTypes with respect to their activation status
     * @return a map with key of NotificationType and value Boolean indicating which types are (de)activated by the user's notification settings
     */
    private Map<NotificationType, Boolean> convertNotificationOptionsToNotificationTypesWithActivationStatus(boolean checkForWebapp, Set<NotificationOption> notificationOptions) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<>();
        NotificationType[] tmpNotificationTypes;
        for (NotificationOption option : notificationOptions) {
            tmpNotificationTypes = this.findCorrespondingNotificationTypesForNotificationOption(option);
            for (NotificationType type : tmpNotificationTypes) {
                resultingMap.put(type, checkForWebapp ? option.isWebapp() : option.isEmail());
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
