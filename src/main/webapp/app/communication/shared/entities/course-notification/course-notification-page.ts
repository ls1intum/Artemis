import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';

export class CourseNotificationPage {
    content: CourseNotification[];
    pageNumber: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
}
