import { Injectable } from '@angular/core';
import {
    ATTACHMENT_CHANGE_TITLE,
    COURSE_ARCHIVE_STARTED_TITLE,
    EXAM_ARCHIVE_STARTED_TITLE,
    EXERCISE_RELEASED_TITLE,
    EXERCISE_PRACTICE_TITLE,
    NEW_ANSWER_POST_FOR_EXERCISE_TITLE,
    NEW_ANSWER_POST_FOR_LECTURE_TITLE,
    NEW_POST_FOR_EXERCISE_TITLE,
    NEW_POST_FOR_LECTURE_TITLE,
    Notification,
    NotificationType,
} from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { SettingId } from 'app/shared/constants/user-settings.constants';

@Injectable({ providedIn: 'root' })
export class NotificationSettingsService {
    /**
     * This is the place where the mapping between SettingIds and notification titles happens on the client side
     * Each SettingIds can be based on multiple different notification titles (based on NotificationTypes)
     */
    private static NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TITLE_MAP: Map<SettingId, string[]> = new Map([
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED, [EXERCISE_RELEASED_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, [EXERCISE_PRACTICE_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES, [NEW_POST_FOR_EXERCISE_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES, [NEW_ANSWER_POST_FOR_EXERCISE_TITLE]],
        [SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, [ATTACHMENT_CHANGE_TITLE]],
        [SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE, [NEW_POST_FOR_LECTURE_TITLE]],
        [SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE, [NEW_ANSWER_POST_FOR_LECTURE_TITLE]],
        [SettingId.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED, [EXAM_ARCHIVE_STARTED_TITLE, COURSE_ARCHIVE_STARTED_TITLE]],
    ]);

    // TODO remove the settings when the corresponding email template is implemented
    static NOTIFICATION_SETTINGS_WITHOUT_EMAIL_SUPPORT: Set<SettingId> = new Set<SettingId>([
        SettingId.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
        SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES,
        SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
        SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE,
        SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE,
    ]);

    /**
     * Creates an updates map that indicates which notifications (titles) are (de)activated in the current notification settings
     * @param notificationSettings will be mapped to their respective title and create a new updated map
     * @return the updated map
     */
    public createUpdatedNotificationTitleActivationMap(notificationSettings: NotificationSetting[]): Map<string, boolean> {
        const updatedMap: Map<string, boolean> = new Map<string, boolean>();
        let tmpNotificationTitles: string[];

        for (let i = 0; i < notificationSettings.length; i++) {
            tmpNotificationTitles = NotificationSettingsService.NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TITLE_MAP.get(notificationSettings[i].settingId) ?? [];
            if (tmpNotificationTitles.length > 0) {
                tmpNotificationTitles.forEach((tmpNotificationTitle) => {
                    updatedMap.set(tmpNotificationTitle, notificationSettings[i].webapp!);
                });
            }
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
}
