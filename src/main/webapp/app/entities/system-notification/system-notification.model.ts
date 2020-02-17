import { Moment } from 'moment';
import { Notification, NotificationType } from 'app/entities/notification/notification.model';

export const enum SystemNotificationType {
    WARNING = 'WARNING',
    INFO = 'INFO',
}

export class SystemNotification extends Notification {
    public expireDate: Moment | null;
    public type: SystemNotificationType;

    constructor() {
        super(NotificationType.SYSTEM);
    }
}
