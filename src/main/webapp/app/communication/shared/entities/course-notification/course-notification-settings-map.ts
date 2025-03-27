import { CourseNotificationChannelSetting } from 'app/communication/shared/entities/course-notification/course-notification-channel-setting';

export interface CourseNotificationSettingsMap {
    [notificationType: string]: CourseNotificationChannelSetting;
}
