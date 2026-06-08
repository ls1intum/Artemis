import { CourseNotificationSettingsMap } from 'app/notification/shared/entities/course-notification/course-notification-settings-map';

export class CourseNotificationSettingInfo {
    selectedPreset: number;
    notificationTypeChannels: CourseNotificationSettingsMap;
}
