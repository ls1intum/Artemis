package de.tum.in.www1.artemis.service;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.UserOption;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

@Service
public class UserSettingsService {

    public Set<NotificationType> findDeactivatedNotificationTypes(UserOption[] userOptions) {
        Map<NotificationType, Boolean> userOptionWitchActivationStatusMap = convertNotificationUserOptionsToNotificationTypesWithActivationStatus(userOptions);
        Set<NotificationType> deactivatedNotificationTypes = new HashSet<>();
        userOptionWitchActivationStatusMap.forEach((notificationType, isActivated) -> {
            if (!isActivated) {
                deactivatedNotificationTypes.add(notificationType);
            }
        });
        return deactivatedNotificationTypes;
    }

    public Map<NotificationType, Boolean> convertNotificationUserOptionsToNotificationTypesWithActivationStatus(UserOption[] userOptions) {
        Map<NotificationType, Boolean> resultingMap = new HashMap<NotificationType, Boolean>();
        NotificationType[] tmpNotificationTypes;
        for (int i = 0; i < userOptions.length; i++) {
            tmpNotificationTypes = this.findCorrespondingNotificationTypesForUserOption(userOptions[i]);
            for (NotificationType notificationType : tmpNotificationTypes) {
                resultingMap.put(notificationType, userOptions[i].isWebapp());
            }
        }
        return resultingMap;
    }

    /**
     * This is the place where the mapping between (notification) userOption and NotificationType happens on the server side
     * Each notification based userOption can be based on multiple different NotificationTypes
     * @param userOption which corresponding NotificationTypes should be found
     * @return the corresponding NotificationType(s)
     */
    public NotificationType[] findCorrespondingNotificationTypesForUserOption(UserOption userOption) {
        switch (userOption.getOptionSpecifier()) {
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
}
