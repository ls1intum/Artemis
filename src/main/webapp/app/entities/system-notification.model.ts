import { Notification, NotificationType } from 'app/entities/notification.model';
import dayjs from 'dayjs/esm';

export const enum SystemNotificationType {
    WARNING = 'WARNING',
    INFO = 'INFO',
}

export class SystemNotification extends Notification {
    public expireDate?: dayjs.Dayjs;
    public type?: SystemNotificationType;

    constructor() {
        super(NotificationType.SYSTEM);
    }
}
