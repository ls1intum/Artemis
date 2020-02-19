import { User } from 'app/core/user/user.model';
import { Notification, NotificationType } from 'app/entities/notification.model';

export class SingleUserNotification extends Notification {
    public id: number;
    public recipient: User;

    constructor() {
        super(NotificationType.SINGLE);
    }
}
