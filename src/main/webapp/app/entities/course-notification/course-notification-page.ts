import { CourseNotification } from 'app/entities/course-notification/course-notification';

export class CourseNotificationPage {
    content: CourseNotification[];
    pageNumber: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
}
