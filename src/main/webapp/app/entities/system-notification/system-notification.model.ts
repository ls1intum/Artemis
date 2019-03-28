import { Moment } from 'moment';
import { Notification, NotificationType } from 'app/entities/notification';

export const enum SystemNotificationType {
    WARNING = 'WARNING',
    INFO = 'INFO'
}

export class SystemNotification extends Notification{
    public expireDate: Moment;
    public type: SystemNotificationType;
    constructor() {
        super(NotificationType.SYSTEM);
    }
}
