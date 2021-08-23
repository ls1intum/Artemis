import { Notification, NotificationType, OriginalNotificationType } from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';

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
