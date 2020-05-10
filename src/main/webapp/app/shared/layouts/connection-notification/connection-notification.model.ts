import { Moment } from 'moment';
import { Notification, NotificationType } from 'app/entities/notification.model';

/**
 * @enum ConnectionNotificationType
 * Represents the connection notification types.
 */
export const enum ConnectionNotificationType {
    DISCONNECTED = 'DISCONNECTED',
    RECONNECTED = 'RECONNECTED',
    CONNECTED = 'CONNECTED',
}

export class ConnectionNotification extends Notification {
    public expireDate: Moment;
    public type: ConnectionNotificationType | null;

    constructor() {
        super(NotificationType.CONNECTION);
    }
}
