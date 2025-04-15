import { Notification, NotificationType } from 'app/core/shared/entities/notification.model';
import { Course } from 'app/core/course/shared/entities/course.model';

export const enum GroupNotificationType {
    INSTRUCTOR = 'INSTRUCTOR',
    EDITOR = 'EDITOR',
    TA = 'TA',
    STUDENT = 'STUDENT',
}

export class GroupNotification extends Notification {
    type?: GroupNotificationType;
    course?: Course;

    constructor() {
        super(NotificationType.GROUP);
    }
}
