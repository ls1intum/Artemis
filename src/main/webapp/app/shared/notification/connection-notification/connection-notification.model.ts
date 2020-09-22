import { Moment } from 'moment';
import { Notification, NotificationType } from 'app/entities/notification.model';

export const enum ConnectionNotificationType {
    DISCONNECTED = 'DISCONNECTED',
    RECONNECTED = 'RECONNECTED',
    CONNECTED = 'CONNECTED',
}

export class ConnectionNotification extends Notification {
    public expireDate: Moment;
    public type?: ConnectionNotificationType;

    constructor() {
        super(NotificationType.CONNECTION);
    }
}
