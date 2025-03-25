import { CourseNotificationSettingPreset } from 'app/entities/course-notification/course-notification-setting-preset';

export class CourseNotificationInfo {
    notificationTypes: Record<number, string>;
    presets: CourseNotificationSettingPreset[];
}
