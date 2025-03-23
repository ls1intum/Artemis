import dayjs from 'dayjs/esm';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';

export class CourseNotification {
    public notificationId?: number;
    public courseId?: number;
    public courseName?: string;
    public courseIconUrl?: string;
    public notificationType?: string;
    public category?: CourseNotificationCategory;
    public status?: CourseNotificationViewingStatus;
    public creationDate?: dayjs.Dayjs;
    public parameters?: Record<string, string | number | boolean | undefined>;

    constructor(
        notificationId: number,
        courseId: number,
        notificationType: string,
        courseNotificationCategory: CourseNotificationCategory,
        status: CourseNotificationViewingStatus,
        creationDate: dayjs.Dayjs,
        parameters: Record<string, string | number | boolean | undefined>,
    ) {
        this.status = status;
        this.notificationId = notificationId;
        this.courseId = courseId;
        if (parameters['courseTitle']) {
            this.courseName = parameters['courseTitle'] as string;
        }
        if (parameters['courseIconUrl'] !== undefined) {
            this.courseIconUrl = parameters['courseIconUrl'] as string;
        }
        this.notificationType = notificationType;
        this.category = courseNotificationCategory;
        this.creationDate = creationDate;
        this.parameters = parameters;
    }
}
