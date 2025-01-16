import { Injectable, inject } from '@angular/core';
import {
    ATTACHMENT_CHANGE_TITLE,
    CONVERSATION_ADD_USER_CHANNEL_TITLE,
    CONVERSATION_ADD_USER_GROUP_CHAT_TITLE,
    CONVERSATION_CREATE_GROUP_CHAT_TITLE,
    CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE,
    CONVERSATION_DELETE_CHANNEL_TITLE,
    CONVERSATION_REMOVE_USER_CHANNEL_TITLE,
    CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE,
    COURSE_ARCHIVE_STARTED_TITLE,
    EXAM_ARCHIVE_STARTED_TITLE,
    EXERCISE_PRACTICE_TITLE,
    EXERCISE_RELEASED_TITLE,
    EXERCISE_SUBMISSION_ASSESSED_TITLE,
    MENTIONED_IN_MESSAGE_TITLE,
    NEW_COURSE_POST_TITLE,
    NEW_EXAM_POST_TITLE,
    NEW_EXERCISE_POST_TITLE,
    NEW_LECTURE_POST_TITLE,
    NEW_MESSAGE_TITLE,
    NEW_REPLY_FOR_COURSE_POST_TITLE,
    NEW_REPLY_FOR_EXAM_POST_TITLE,
    NEW_REPLY_FOR_EXERCISE_POST_TITLE,
    NEW_REPLY_FOR_LECTURE_POST_TITLE,
    NEW_REPLY_MESSAGE_TITLE,
    Notification,
    NotificationType,
    TUTORIAL_GROUP_ASSIGNED_TITLE,
    TUTORIAL_GROUP_DELETED_TITLE,
    TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE,
    TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE,
    TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE,
    TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE,
    TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE,
    TUTORIAL_GROUP_UNASSIGNED_TITLE,
    TUTORIAL_GROUP_UPDATED_TITLE,
} from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting } from 'app/shared/user-settings/user-settings.model';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject } from 'rxjs';

export const reloadNotificationSideBarMessage = 'reloadNotificationsInNotificationSideBar';

@Injectable({ providedIn: 'root' })
export class NotificationSettingsService {
    private userSettingsService = inject(UserSettingsService);

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
        [SettingId.NOTIFICATION__EXAM_NOTIFICATION__NEW_EXAM_POST, [NEW_EXAM_POST_TITLE]],
        [SettingId.NOTIFICATION__EXAM_NOTIFICATION__NEW_REPLY_FOR_EXAM_POST, [NEW_REPLY_FOR_EXAM_POST_TITLE]],
        [SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST, [NEW_COURSE_POST_TITLE]],
        [SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST, [NEW_REPLY_FOR_COURSE_POST_TITLE]],
        [SettingId.NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED, [EXAM_ARCHIVE_STARTED_TITLE, COURSE_ARCHIVE_STARTED_TITLE]],

        [
            SettingId.NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
            [TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE],
        ],
        [
            SettingId.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
            [TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE, TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE],
        ],
        [
            SettingId.NOTIFICATION__USER_NOTIFICATION__CONVERSATION_MESSAGE,
            [
                NEW_MESSAGE_TITLE,
                CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE,
                CONVERSATION_CREATE_GROUP_CHAT_TITLE,
                CONVERSATION_ADD_USER_GROUP_CHAT_TITLE,
                CONVERSATION_ADD_USER_CHANNEL_TITLE,
                CONVERSATION_DELETE_CHANNEL_TITLE,
                CONVERSATION_REMOVE_USER_CHANNEL_TITLE,
                CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE,
            ],
        ],
        [SettingId.NOTIFICATION__USER_NOTIFICATION__NEW_REPLY_IN_CONVERSATION_MESSAGE, [NEW_REPLY_MESSAGE_TITLE]],
        [SettingId.NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_ASSIGN_UNASSIGN, [TUTORIAL_GROUP_ASSIGNED_TITLE, TUTORIAL_GROUP_UNASSIGNED_TITLE]],
        [SettingId.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE, [TUTORIAL_GROUP_DELETED_TITLE, TUTORIAL_GROUP_UPDATED_TITLE]],
        [SettingId.NOTIFICATION__USER_NOTIFICATION__USER_MENTION, [MENTIONED_IN_MESSAGE_TITLE]],
    ]);

    private currentNotificationSettings: NotificationSetting[] = [];
    private currentNotificationSettingsSubject = new ReplaySubject<NotificationSetting[]>(1);

    private notificationTitleActivationMap: Map<string, boolean> = new Map<string, boolean>();

    constructor() {
        this.listenForNotificationSettingsChanges();
    }

    public refreshNotificationSettings(): void {
        this.userSettingsService.loadSettings(UserSettingsCategory.NOTIFICATION_SETTINGS).subscribe({
            next: (res: HttpResponse<Setting[]>) => {
                this.currentNotificationSettings = this.userSettingsService.loadSettingsSuccessAsIndividualSettings(
                    res.body!,
                    UserSettingsCategory.NOTIFICATION_SETTINGS,
                ) as NotificationSetting[];

                this.notificationTitleActivationMap = this.createUpdatedNotificationTitleActivationMap();
                this.currentNotificationSettingsSubject.next(this.currentNotificationSettings);
            },
        });
    }

    getNotificationSettings(): NotificationSetting[] {
        return this.currentNotificationSettings;
    }

    getNotificationSettingsUpdates(): Observable<NotificationSetting[]> {
        return this.currentNotificationSettingsSubject.asObservable();
    }

    /**
     * Creates an updates map that indicates which notifications (titles) are (de)activated in the current notification settings
     * @param notificationSettings will be mapped to their respective title and create a new updated map
     * @return the updated map
     */
    private createUpdatedNotificationTitleActivationMap(): Map<string, boolean> {
        const updatedMap: Map<string, boolean> = new Map<string, boolean>();
        let tmpNotificationTitles: string[];

        for (let i = 0; i < this.currentNotificationSettings.length; i++) {
            tmpNotificationTitles = NotificationSettingsService.NOTIFICATION_SETTING_ID_TO_NOTIFICATION_TITLE_MAP.get(this.currentNotificationSettings[i].settingId) ?? [];
            if (tmpNotificationTitles.length > 0) {
                tmpNotificationTitles.forEach((tmpNotificationTitle) => {
                    updatedMap.set(tmpNotificationTitle, this.currentNotificationSettings[i].webapp!);
                });
            }
        }
        return updatedMap;
    }

    /**
     * Checks if the notification (i.e. its title (only for Group/Single-User Notifications)) is activated in the notification settings
     * @param notification which should be checked if it is activated in the notification settings of the current user
     * @return true if this notification (title) is activated in the settings, else return false
     */
    public isNotificationAllowedBySettings(notification: Notification): boolean {
        if (
            notification instanceof GroupNotification ||
            notification.notificationType === NotificationType.GROUP ||
            notification.notificationType === NotificationType.SINGLE ||
            notification.notificationType === NotificationType.CONVERSATION
        ) {
            if (notification.title) {
                return this.notificationTitleActivationMap.get(notification.title) ?? true;
            }
        }
        return true;
    }

    /**
     * Subscribes and listens for changes related to notifications
     */
    private listenForNotificationSettingsChanges(): void {
        this.userSettingsService.userSettingsChangeEvent.subscribe((changeMessage) => {
            if (changeMessage === reloadNotificationSideBarMessage) {
                this.refreshNotificationSettings();
            }
        });
    }
}
