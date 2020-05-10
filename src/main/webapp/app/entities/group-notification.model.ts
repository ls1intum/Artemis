import { Notification, NotificationType } from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';

/**
 * defines different types of group notification
 */
export const enum GroupNotificationType {
    INSTRUCTOR = 'INSTRUCTOR',
    TA = 'TA',
    STUDENT = 'STUDENT',
}

export class GroupNotification extends Notification {
    type: GroupNotificationType;
    course: Course;

    constructor() {
        super(NotificationType.GROUP);
    }
}
