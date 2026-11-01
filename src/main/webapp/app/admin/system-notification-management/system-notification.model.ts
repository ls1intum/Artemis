import dayjs from 'dayjs/esm';
import { SystemNotificationTypeEnum } from 'app/openapi/model/system-notification';

export const SystemNotificationType = {
    WARNING: SystemNotificationTypeEnum.Warning,
    INFO: SystemNotificationTypeEnum.Info,
};

export class SystemNotification {
    public id?: number;
    public title?: string;
    public text?: string;
    public notificationDate?: dayjs.Dayjs;
    public expireDate?: dayjs.Dayjs;
    public type?: SystemNotificationTypeEnum;
}
