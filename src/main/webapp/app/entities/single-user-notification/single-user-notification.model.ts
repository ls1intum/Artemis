import { User } from 'app/core/user/user.model';
import { Notification } from 'app/entities/notification';

export class SingleUserNotification extends Notification {
    public id: number;
    public recipient: User;

    constructor() {
        super();
    }
}
