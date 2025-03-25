import { CourseNotificationSettingsMap } from 'app/entities/course-notification/course-notification-settings-map';

export class CourseNotificationSettingInfo {
    selectedPreset: number;
    notificationTypeChannels: CourseNotificationSettingsMap;
}
