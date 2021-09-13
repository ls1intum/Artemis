import { Injectable } from '@angular/core';
import {
    ATTACHMENT_CHANGE_TITLE,
    COURSE_ARCHIVE_STARTED_TITLE,
    EXAM_ARCHIVE_STARTED_TITLE,
    EXERCISE_CREATED_TITLE,
    EXERCISE_PRACTICE_TITLE,
    NEW_ANSWER_POST_FOR_EXERCISE_TITLE,
    NEW_ANSWER_POST_FOR_LECTURE_TITLE,
    NEW_POST_FOR_EXERCISE_TITLE,
    NEW_POST_FOR_LECTURE_TITLE,
    Notification,
    NotificationType,
} from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { OptionSpecifier } from 'app/shared/constants/user-settings.constants';

@Injectable({ providedIn: 'root' })
export class NotificationSettingsService {
    /**
     * Creates an updates map that indicates which notifications (titles) are (de)activated in the current notification settings
     * @param notificationOptionCores will be mapped to their respective title and create a new updated map
     * @return the updated map
     */
    public createUpdatedNotificationTitleActivationMap(notificationOptionCores: NotificationOptionCore[]): Map<string, boolean> {
        const updatedMap: Map<string, boolean> = new Map<string, boolean>();
        let tmpNotificationTitles: string[];

        for (let i = 0; i < notificationOptionCores.length; i++) {
            tmpNotificationTitles = NotificationSettingsService.findCorrespondingNotificationTypesForNotificationOptionCore(notificationOptionCores[i]);
            tmpNotificationTitles.forEach((tmpNotificationTitle) => {
                updatedMap.set(tmpNotificationTitle, notificationOptionCores[i].webapp);
            });
        }
        return updatedMap;
    }

    /**
     * Checks if the notification (i.e. its title (only for Group/Single-User Notifications)) is activated in the notification settings
     * @param notification which should be checked if it is activated in the notification settings of the current user
     * @param notificationTitleActivationMap hold the information of the saved notification settings and their status
     * @return true if this notification (title) is activated in the settings, else return false
     */
    public isNotificationAllowedBySettings(notification: Notification, notificationTitleActivationMap: Map<string, boolean>): boolean {
        if (notification instanceof GroupNotification || notification.notificationType === NotificationType.GROUP || notification.notificationType === NotificationType.SINGLE) {
            if (notification.title) {
                return notificationTitleActivationMap.get(notification.title) ?? true;
            }
        }
        return true;
    }

    /**
     * This is the place where the mapping between NotificationOptionCores and NotificationTypes happens on the client side
     * Each NotificationOptionCore can be based on multiple different NotificationTypes
     * @param notificationOptionCore which corresponding NotificationTypes should be found
     * @return the corresponding NotificationType(s)
     */
    private static findCorrespondingNotificationTypesForNotificationOptionCore(notificationOptionCore: NotificationOptionCore): string[] {
        switch (notificationOptionCore.optionSpecifier) {
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED: {
                return [EXERCISE_CREATED_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE: {
                return [EXERCISE_PRACTICE_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES: {
                return [NEW_POST_FOR_EXERCISE_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES: {
                return [NEW_ANSWER_POST_FOR_EXERCISE_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES: {
                return [ATTACHMENT_CHANGE_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE: {
                return [NEW_POST_FOR_LECTURE_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE: {
                return [NEW_ANSWER_POST_FOR_LECTURE_TITLE];
            }
            case OptionSpecifier.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED: {
                return [EXAM_ARCHIVE_STARTED_TITLE, COURSE_ARCHIVE_STARTED_TITLE];
            }
            default: {
                return [];
            }
        }
    }
}
