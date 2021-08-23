import { Injectable } from '@angular/core';
import { Notification, NotificationType, OriginalNotificationType } from 'app/entities/notification.model';
import { OptionCore } from 'app/shared/user-settings/user-settings.service';
import { GroupNotification } from 'app/entities/group-notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationSettingsService {
    /**
     * Updates the provided map which indicates which OriginalNotificationTypes are (de)activated in the current notification settings
     * @param notificationOptionCores will be mapped to their respective OriginalNotificationTypes and create a new updated map
     * @return the updated map
     */
    public updateOriginalNotificationTypeActivationMap(notificationOptionCores: OptionCore[]): Map<OriginalNotificationType, boolean> {
        const updatedMap: Map<OriginalNotificationType, boolean> = new Map<OriginalNotificationType, boolean>();
        let tmpOriginalNotificationTypes: OriginalNotificationType[];

        for (let i = 0; i < notificationOptionCores.length; i++) {
            tmpOriginalNotificationTypes = this.findCorrespondingNotificationTypesForUserOption(notificationOptionCores[i]);
            tmpOriginalNotificationTypes.forEach((originalNotificationType) => {
                updatedMap.set(originalNotificationType, notificationOptionCores[i].webapp);
            });
        }
        return updatedMap;
    }

    /**
     * Checks if the notification (i.e. its OriginalNotificationType (only for Group/Single-User Notifications)) is disabled in the notification settings
     * @param notification which should be checked if it is disabled in the notification settings of the current user
     * @param originalNotificationTypeActivationMap hold the information of the saved notification settings and their status
     * @return true if this OriginalNotificationType is disabled in the settings, else return false
     */
    public isNotificationBlockedBySettings(notification: Notification, originalNotificationTypeActivationMap: Map<OriginalNotificationType, boolean>): boolean {
        if (notification instanceof GroupNotification || notification.notificationType === NotificationType.GROUP || notification.notificationType === NotificationType.SINGLE) {
            if (notification.originalNotificationType) {
                // if the type is in the map return the inverse boolean (false in map -> is deactivated? = true)
                // else return false to allow every other type that is not mentioned in the map (untouched options + legacy null) to pass
                if (!originalNotificationTypeActivationMap.get(notification.originalNotificationType) ?? false) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This is the place where the mapping between (notification) userOption and NotificationType happens on the client side
     * Each notification based userOption can be based on multiple different NotificationTypes
     * @param userOption which corresponding NotificationTypes should be found
     * @return the corresponding NotificationType(s)
     */
    private findCorrespondingNotificationTypesForUserOption(optionCore: OptionCore): OriginalNotificationType[] {
        switch (optionCore.optionSpecifier) {
            case 'notification.exercise-notification.exercise-created-or-started': {
                return [OriginalNotificationType.EXERCISE_CREATED];
            }
            case 'notification.exercise-notification.exercise-open-for-practice': {
                return [OriginalNotificationType.EXERCISE_PRACTICE];
            }
            case 'notification.exercise-notification.new-post-exercises': {
                return [OriginalNotificationType.NEW_POST_FOR_EXERCISE];
            }
            case 'notification.exercise-notification.new-answer-post-exercises': {
                return [OriginalNotificationType.NEW_ANSWER_POST_FOR_EXERCISE];
            }
            case 'notification.lecture-notification.attachment-changes': {
                return [OriginalNotificationType.ATTACHMENT_CHANGE];
            }
            case 'notification.lecture-notification.new-post-for-lecture': {
                return [OriginalNotificationType.NEW_POST_FOR_LECTURE];
            }
            case 'notification.lecture-notification.new-answer-post-for-lecture': {
                return [OriginalNotificationType.NEW_ANSWER_POST_FOR_LECTURE];
            }
            case 'notification.instructor-exclusive-notification.course-and-exam-archiving-started': {
                return [OriginalNotificationType.EXAM_ARCHIVE_STARTED, OriginalNotificationType.COURSE_ARCHIVE_STARTED];
            }
            default: {
                return [];
            }
        }
    }
}
