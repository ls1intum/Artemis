import { CourseNotificationSettingPreset } from 'app/notification/shared/entities/course-notification/course-notification-setting-preset';

export interface CourseNotificationInfo {
    notificationTypes: Record<number, string>;
    presets: CourseNotificationSettingPreset[];
}
