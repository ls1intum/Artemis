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

/**
 * DTO containing the relevant information of a system notification.
 */
export interface SystemNotificationDTO {
    id?: number;
    title?: string;
    text?: string;
    notificationDate?: dayjs.Dayjs;
    expireDate?: dayjs.Dayjs;
    type?: SystemNotificationType;
}
