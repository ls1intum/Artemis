import dayjs from 'dayjs/esm';
import { Notification, NotificationType } from 'app/entities/notification.model';

export const enum ConnectionNotificationType {
    DISCONNECTED = 'DISCONNECTED',
    RECONNECTED = 'RECONNECTED',
    CONNECTED = 'CONNECTED',
}

export class ConnectionNotification extends Notification {
    public expireDate: dayjs.Dayjs;
    public type?: ConnectionNotificationType;

    constructor() {
        super(NotificationType.CONNECTION);
    }
}
