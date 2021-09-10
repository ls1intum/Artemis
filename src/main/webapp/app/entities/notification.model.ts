import * as dayjs from 'dayjs';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export enum NotificationType {
    SYSTEM = 'system',
    CONNECTION = 'connection',
    GROUP = 'group',
    SINGLE = 'single',
}

export class Notification implements BaseEntity {
    public id?: number;
    public notificationType?: NotificationType;
    public title?: string;
    public text?: string;
    public notificationDate?: dayjs.Dayjs;
    public target?: string;
    public author?: User;

    protected constructor(notificationType: NotificationType) {
        this.notificationType = notificationType;
    }
}
