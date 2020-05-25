import { Notification } from 'app/entities/notification.model';

export const enum GroupNotificationType {
    INSTRUCTOR = 'INSTRUCTOR',
    TA = 'TA',
    STUDENT = 'STUDENT',
}

export class GroupNotification extends Notification {
    public type: GroupNotificationType;
}
