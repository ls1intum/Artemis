import { Notification, NotificationType } from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';

export const enum DatabaseNotificationType {
    INSTRUCTOR = 'INSTRUCTOR',
    EDITOR = 'EDITOR',
    TA = 'TA',
    STUDENT = 'STUDENT',
}

export class GroupNotification extends Notification {
    type?: DatabaseNotificationType;
    course?: Course;

    constructor() {
        super(NotificationType.GROUP);
    }
}
