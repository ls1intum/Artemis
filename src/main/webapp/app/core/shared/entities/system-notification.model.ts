import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';

export const enum SystemNotificationType {
    WARNING = 'WARNING',
    INFO = 'INFO',
}

export class SystemNotification {
    public id?: number;
    public title?: string;
    public text?: string;
    public notificationDate?: dayjs.Dayjs;
    public author?: User;
    public expireDate?: dayjs.Dayjs;
    public type?: SystemNotificationType;
}
