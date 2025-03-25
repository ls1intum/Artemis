import { CourseNotificationChannelSetting } from 'app/entities/course-notification/course-notification-channel-setting';

export interface CourseNotificationSettingsMap {
    [notificationType: string]: CourseNotificationChannelSetting;
}
