import { CourseNotificationSettingsMap } from 'app/communication/shared/entities/course-notification/course-notification-settings-map';

export class CourseNotificationSettingInfo {
    selectedPreset: number;
    notificationTypeChannels: CourseNotificationSettingsMap;
}
