import { Injectable } from '@angular/core';
import {
    ATTACHMENT_CHANGE_TITLE,
    COURSE_ARCHIVE_STARTED_TITLE,
    EXAM_ARCHIVE_STARTED_TITLE,
    EXERCISE_PRACTICE_TITLE,
    EXERCISE_RELEASED_TITLE,
    EXERCISE_SUBMISSION_ASSESSED_TITLE,
    NEW_COURSE_POST_TITLE,
    NEW_EXERCISE_POST_TITLE,
    NEW_LECTURE_POST_TITLE,
    NEW_REPLY_FOR_COURSE_POST_TITLE,
    NEW_REPLY_FOR_EXERCISE_POST_TITLE,
    NEW_REPLY_FOR_LECTURE_POST_TITLE,
    Notification,
    NotificationType,
    TUTORIAL_GROUP_DELETED_TITLE,
    TUTORIAL_GROUP_DEREGISTERED_STUDENT_TITLE,
    TUTORIAL_GROUP_DEREGISTERED_TUTOR_TITLE,
    TUTORIAL_GROUP_REGISTERED_TUTOR_TITLE,
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
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED, [EXERCISE_SUBMISSION_ASSESSED_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED, [EXERCISE_RELEASED_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE, [EXERCISE_PRACTICE_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST, [NEW_EXERCISE_POST_TITLE]],
        [SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST, [NEW_REPLY_FOR_EXERCISE_POST_TITLE]],
        [SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES, [ATTACHMENT_CHANGE_TITLE]],
        [SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST, [NEW_LECTURE_POST_TITLE]],
        [SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST, [NEW_REPLY_FOR_LECTURE_POST_TITLE]],
        [SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST, [NEW_COURSE_POST_TITLE]],
        [SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST, [NEW_REPLY_FOR_COURSE_POST_TITLE]],
        [SettingId.NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED, [EXAM_ARCHIVE_STARTED_TITLE, COURSE_ARCHIVE_STARTED_TITLE]],
        [SettingId.NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION, [TUTORIAL_GROUP_REGISTERED_TUTOR_TITLE, TUTORIAL_GROUP_DEREGISTERED_TUTOR_TITLE]],
        [SettingId.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION, [TUTORIAL_GROUP_DEREGISTERED_STUDENT_TITLE, TUTORIAL_GROUP_DEREGISTERED_STUDENT_TITLE]],
        [SettingId.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE, [TUTORIAL_GROUP_DELETED_TITLE, TUTORIAL_GROUP_DELETED_TITLE]],
    ]);

    // needed to make it possible for other services to get the latest settings without calling the server additional times
    private newestNotificationSettings: NotificationSetting[] = [];

    public getNotificationSettings(): NotificationSetting[] {
        return this.newestNotificationSettings;
    }

    public setNotificationSettings(notificationSettings: NotificationSetting[]) {
        this.newestNotificationSettings = notificationSettings;
    }

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
