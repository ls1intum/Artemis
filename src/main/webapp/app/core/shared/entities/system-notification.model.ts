import dayjs from 'dayjs/esm';
import { Notification, NotificationType } from 'app/core/shared/entities/notification.model';
import { Post } from 'app/entities/metis/post.model';

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

export class ConversationNotification extends Notification {
    public message: Post;

    constructor() {
        super(NotificationType.CONVERSATION);
    }
}
