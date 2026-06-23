import { CourseNotificationChannelSetting } from 'app/notification/shared/entities/course-notification/course-notification-channel-setting';

export interface CourseNotificationSettingsMap {
    [notificationType: string]: CourseNotificationChannelSetting;
}
