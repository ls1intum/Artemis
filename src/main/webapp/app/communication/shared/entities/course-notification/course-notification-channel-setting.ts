import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';

export interface CourseNotificationChannelSetting {
    [CourseNotificationChannel.PUSH]: boolean;
    [CourseNotificationChannel.EMAIL]: boolean;
    [CourseNotificationChannel.WEBAPP]: boolean;
}
