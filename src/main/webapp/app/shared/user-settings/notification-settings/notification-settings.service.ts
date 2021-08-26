import { Injectable } from '@angular/core';
import { Notification, NotificationType, OriginalNotificationType } from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { OptionSpecifier } from 'app/shared/constants/user-settings.constants';

@Injectable({ providedIn: 'root' })
export class NotificationSettingsService {
    /**
     * Updates the provided map which indicates which OriginalNotificationTypes are (de)activated in the current notification settings
     * @param notificationOptionCores will be mapped to their respective OriginalNotificationTypes and create a new updated map
     * @return the updated map
     */
    public updateOriginalNotificationTypeActivationMap(notificationOptionCores: NotificationOptionCore[]): Map<OriginalNotificationType, boolean> {
        const updatedMap: Map<OriginalNotificationType, boolean> = new Map<OriginalNotificationType, boolean>();
        let tmpOriginalNotificationTypes: OriginalNotificationType[];

        for (let i = 0; i < notificationOptionCores.length; i++) {
            tmpOriginalNotificationTypes = this.findCorrespondingNotificationTypesForNotificationOptionCore(notificationOptionCores[i]);
            tmpOriginalNotificationTypes.forEach((originalNotificationType) => {
                updatedMap.set(originalNotificationType, notificationOptionCores[i].webapp);
            });
        }
        return updatedMap;
    }

    /**
     * Checks if the notification (i.e. its OriginalNotificationType (only for Group/Single-User Notifications)) is activated in the notification settings
     * @param notification which should be checked if it is activated in the notification settings of the current user
     * @param originalNotificationTypeActivationMap hold the information of the saved notification settings and their status
     * @return true if this OriginalNotificationType is activated in the settings, else return false
     */
    public isNotificationAllowedBySettings(notification: Notification, originalNotificationTypeActivationMap: Map<OriginalNotificationType, boolean>): boolean {
        if (notification instanceof GroupNotification || notification.notificationType === NotificationType.GROUP || notification.notificationType === NotificationType.SINGLE) {
            if (notification.originalNotificationType) {
                return originalNotificationTypeActivationMap.get(notification.originalNotificationType) ?? true;
            }
        }
        return true;
    }

    /**
     * This is the place where the mapping between NotificationOptionCores and NotificationTypes happens on the client side
     * Each NotificationOptionCore can be based on multiple different NotificationTypes
     * @param NotificationOptionCore which corresponding NotificationTypes should be found
     * @return the corresponding NotificationType(s)
     */
    private findCorrespondingNotificationTypesForNotificationOptionCore(notificationOptionCore: NotificationOptionCore): OriginalNotificationType[] {
        switch (notificationOptionCore.optionSpecifier) {
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED: {
                return [OriginalNotificationType.EXERCISE_CREATED];
            }
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE: {
                return [OriginalNotificationType.EXERCISE_PRACTICE];
            }
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES: {
                return [OriginalNotificationType.NEW_POST_FOR_EXERCISE];
            }
            case OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES: {
                return [OriginalNotificationType.NEW_ANSWER_POST_FOR_EXERCISE];
            }
            case OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES: {
                return [OriginalNotificationType.ATTACHMENT_CHANGE];
            }
            case OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE: {
                return [OriginalNotificationType.NEW_POST_FOR_LECTURE];
            }
            case OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE: {
                return [OriginalNotificationType.NEW_ANSWER_POST_FOR_LECTURE];
            }
            case OptionSpecifier.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED: {
                return [OriginalNotificationType.EXAM_ARCHIVE_STARTED, OriginalNotificationType.COURSE_ARCHIVE_STARTED];
            }
            default: {
                return [];
            }
        }
    }
}
